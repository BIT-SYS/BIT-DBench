/**
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.catalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HServerAddress;
import org.apache.hadoop.hbase.HServerInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NotAllMetaRegionsOnlineException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.ipc.HRegionInterface;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.Writables;

/**
 * Reads region and assignment information from <code>.META.</code>.
 * <p>
 * Uses the {@link CatalogTracker} to obtain locations and connections to
 * catalogs.
 */
public class MetaReader {
  public static final byte [] META_REGION_PREFIX;
  static {
    // Copy the prefix from FIRST_META_REGIONINFO into META_REGION_PREFIX.
    // FIRST_META_REGIONINFO == '.META.,,1'.  META_REGION_PREFIX == '.META.,'
    int len = HRegionInfo.FIRST_META_REGIONINFO.getRegionName().length - 2;
    META_REGION_PREFIX = new byte [len];
    System.arraycopy(HRegionInfo.FIRST_META_REGIONINFO.getRegionName(), 0,
      META_REGION_PREFIX, 0, len);
  }

  /**
   * @param ct
   * @param tableName A user tablename or a .META. table name.
   * @return Interface on to server hosting the <code>-ROOT-</code> or
   * <code>.META.</code> regions.
   * @throws NotAllMetaRegionsOnlineException
   * @throws IOException
   */
  private static HRegionInterface getCatalogRegionInterface(final CatalogTracker ct,
      final byte [] tableName)
  throws NotAllMetaRegionsOnlineException, IOException {
    return Bytes.equals(HConstants.META_TABLE_NAME, tableName)?
      ct.waitForRootServerConnectionDefault():
      ct.waitForMetaServerConnectionDefault();
  }

  /**
   * @param tableName
   * @return Returns region name to look in for regions for <code>tableName</code>;
   * e.g. if we are looking for <code>.META.</code> regions, we need to look
   * in the <code>-ROOT-</code> region, else if a user table, we need to look
   * in the <code>.META.</code> region.
   */
  private static byte [] getCatalogRegionNameForTable(final byte [] tableName) {
    return Bytes.equals(HConstants.META_TABLE_NAME, tableName)?
      HRegionInfo.ROOT_REGIONINFO.getRegionName():
      HRegionInfo.FIRST_META_REGIONINFO.getRegionName();
  }

  /**
   * @param regionName
   * @return Returns region name to look in for <code>regionName</code>;
   * e.g. if we are looking for <code>.META.,,1</code> region, we need to look
   * in <code>-ROOT-</code> region, else if a user region, we need to look
   * in the <code>.META.,,1</code> region.
   */
  private static byte [] getCatalogRegionNameForRegion(final byte [] regionName) {
    return isMetaRegion(regionName)?
      HRegionInfo.ROOT_REGIONINFO.getRegionName():
      HRegionInfo.FIRST_META_REGIONINFO.getRegionName();
  }

  /**
   * @param regionName
   * @return True if <code>regionName</code> is from <code>.META.</code> table.
   */
  private static boolean isMetaRegion(final byte [] regionName) {
    if (regionName.length < META_REGION_PREFIX.length + 2 /* ',', + '1' */) {
      // Can't be meta table region.
      return false;
    }
    // Compare the prefix of regionName.  If it matches META_REGION_PREFIX prefix,
    // then this is region from .META. table.
    return Bytes.compareTo(regionName, 0, META_REGION_PREFIX.length,
      META_REGION_PREFIX, 0, META_REGION_PREFIX.length) == 0;
  }

  /**
   * Performs a full scan of <code>.META.</code>.
   * <p>
   * Returns a map of every region to it's currently assigned server, according
   * to META.  If the region does not have an assignment it will have a null
   * value in the map.
   *
   * @return map of regions to their currently assigned server
   * @throws IOException
   */
  public static Map<HRegionInfo,HServerAddress> fullScan(CatalogTracker catalogTracker)
  throws IOException {
    HRegionInterface metaServer =
      catalogTracker.waitForMetaServerConnectionDefault();
    Map<HRegionInfo,HServerAddress> allRegions =
      new TreeMap<HRegionInfo,HServerAddress>();
    Scan scan = new Scan();
    scan.addFamily(HConstants.CATALOG_FAMILY);
    long scannerid = metaServer.openScanner(
        HRegionInfo.FIRST_META_REGIONINFO.getRegionName(), scan);
    try {
      Result data;
      while((data = metaServer.next(scannerid)) != null) {
        if (!data.isEmpty()) {
          Pair<HRegionInfo,HServerAddress> region =
            metaRowToRegionPair(data);
          allRegions.put(region.getFirst(), region.getSecond());
        }
      }
    } finally {
      metaServer.close(scannerid);
    }
    return allRegions;
  }

  /**
   * Reads the location of META from ROOT.
   * @param metaServer connection to server hosting ROOT
   * @return location of META in ROOT, null if not available
   * @throws IOException
   */
  public static HServerAddress readMetaLocation(HRegionInterface metaServer)
  throws IOException {
    return readLocation(metaServer, CatalogTracker.ROOT_REGION,
        CatalogTracker.META_REGION);
  }

  /**
   * Reads the location of the specified region from META.
   * @param catalogTracker
   * @param regionName region to read location of
   * @return location of region in META, null if not available
   * @throws IOException
   */
  public static HServerAddress readRegionLocation(CatalogTracker catalogTracker,
      byte [] regionName)
  throws IOException {
    if (isMetaRegion(regionName)) throw new IllegalArgumentException("See readMetaLocation");
    return readLocation(catalogTracker.waitForMetaServerConnectionDefault(),
        CatalogTracker.META_REGION, regionName);
  }

  private static HServerAddress readLocation(HRegionInterface metaServer,
      byte [] catalogRegionName, byte [] regionName)
  throws IOException {
    Result r = null;
    try {
      r = metaServer.get(catalogRegionName,
        new Get(regionName).addColumn(HConstants.CATALOG_FAMILY,
        HConstants.SERVER_QUALIFIER));
    } catch (java.net.ConnectException e) {
      if (e.getMessage() != null &&
          e.getMessage().contains("Connection refused")) {
        // Treat this exception + message as unavailable catalog table. Catch it
        // and fall through to return a null
      } else {
        throw e;
      }
    } catch (IOException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException &&
          e.getCause().getMessage() != null &&
          e.getCause().getMessage().contains("Connection reset by peer")) {
        // Treat this exception + message as unavailable catalog table. Catch it
        // and fall through to return a null
      } else {
        throw e;
      }
    }
    if (r == null || r.isEmpty()) {
      return null;
    }
    byte [] value = r.getValue(HConstants.CATALOG_FAMILY,
      HConstants.SERVER_QUALIFIER);
    return new HServerAddress(Bytes.toString(value));
  }

  /**
   * Gets the region info and assignment for the specified region from META.
   * @param catalogTracker
   * @param regionName
   * @return region info and assignment from META, null if not available
   * @throws IOException
   */
  public static Pair<HRegionInfo, HServerAddress> getRegion(
      CatalogTracker catalogTracker, byte [] regionName)
  throws IOException {
    Get get = new Get(regionName);
    get.addFamily(HConstants.CATALOG_FAMILY);
    byte [] meta = getCatalogRegionNameForRegion(regionName);
    Result r = catalogTracker.waitForMetaServerConnectionDefault().get(meta, get);
    if(r == null || r.isEmpty()) {
      return null;
    }
    return metaRowToRegionPair(r);
  }

  /**
   * @param data A .META. table row.
   * @return A pair of the regioninfo and the server address from <code>data</code>.
   * @throws IOException
   */
  public static Pair<HRegionInfo, HServerAddress> metaRowToRegionPair(
      Result data) throws IOException {
    HRegionInfo info = Writables.getHRegionInfo(
      data.getValue(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER));
    final byte[] value = data.getValue(HConstants.CATALOG_FAMILY,
      HConstants.SERVER_QUALIFIER);
    if (value != null && value.length > 0) {
      HServerAddress server = new HServerAddress(Bytes.toString(value));
      return new Pair<HRegionInfo,HServerAddress>(info, server);
    } else {
      return new Pair<HRegionInfo, HServerAddress>(info, null);
    }
  }

  /**
   * Checks if the specified table exists.  Looks at the META table hosted on
   * the specified server.
   * @param metaServer server hosting meta
   * @param tableName table to check
   * @return true if the table exists in meta, false if not
   * @throws IOException
   */
  public static boolean tableExists(CatalogTracker catalogTracker,
      String tableName)
  throws IOException {
    if (tableName.equals(HTableDescriptor.ROOT_TABLEDESC.getNameAsString()) ||
        tableName.equals(HTableDescriptor.META_TABLEDESC.getNameAsString())) {
      // Catalog tables always exist.
      return true;
    }
    HRegionInterface metaServer =
      catalogTracker.waitForMetaServerConnectionDefault();
    byte[] firstRowInTable = Bytes.toBytes(tableName + ",,");
    Scan scan = new Scan(firstRowInTable);
    scan.addColumn(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
    long scannerid = metaServer.openScanner(
        HRegionInfo.FIRST_META_REGIONINFO.getRegionName(), scan);
    try {
      Result data = metaServer.next(scannerid);
      if (data != null && data.size() > 0) {
        HRegionInfo info = Writables.getHRegionInfo(
          data.getValue(HConstants.CATALOG_FAMILY,
              HConstants.REGIONINFO_QUALIFIER));
        if (info.getTableDesc().getNameAsString().equals(tableName)) {
          // A region for this table already exists. Ergo table exists.
          return true;
        }
      }
      return false;
    } finally {
      metaServer.close(scannerid);
    }
  }

  /**
   * Gets all of the regions of the specified table.
   * @param catalogTracker
   * @param tableName
   * @return Ordered list of {@link HRegionInfo}.
   * @throws IOException
   */
  public static List<HRegionInfo> getTableRegions(CatalogTracker catalogTracker,
      byte [] tableName)
  throws IOException {
    if (Bytes.equals(tableName, HConstants.ROOT_TABLE_NAME)) {
      // If root, do a bit of special handling.
      List<HRegionInfo> list = new ArrayList<HRegionInfo>();
      list.add(HRegionInfo.ROOT_REGIONINFO);
      return list;
    } else if (Bytes.equals(tableName, HConstants.META_TABLE_NAME)) {
      // Same for .META. table
      List<HRegionInfo> list = new ArrayList<HRegionInfo>();
      list.add(HRegionInfo.FIRST_META_REGIONINFO);
      return list;
    }

    // Its a user table.
    HRegionInterface metaServer =
      getCatalogRegionInterface(catalogTracker, tableName);
    List<HRegionInfo> regions = new ArrayList<HRegionInfo>();
    String tableString = Bytes.toString(tableName);
    byte[] firstRowInTable = Bytes.toBytes(tableString + ",,");
    Scan scan = new Scan(firstRowInTable);
    scan.addColumn(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
    long scannerid =
      metaServer.openScanner(getCatalogRegionNameForTable(tableName), scan);
    try {
      Result data;
      while((data = metaServer.next(scannerid)) != null) {
        if (data != null && data.size() > 0) {
          HRegionInfo info = Writables.getHRegionInfo(
              data.getValue(HConstants.CATALOG_FAMILY,
                  HConstants.REGIONINFO_QUALIFIER));
          if (info.getTableDesc().getNameAsString().equals(tableString)) {
            regions.add(info);
          } else {
            break;
          }
        }
      }
      return regions;
    } finally {
      metaServer.close(scannerid);
    }
  }

  /**
   * @param catalogTracker
   * @param tableName
   * @return Return list of regioninfos and server addresses.
   * @throws IOException
   * @throws InterruptedException
   */
  public static List<Pair<HRegionInfo, HServerAddress>>
  getTableRegionsAndLocations(CatalogTracker catalogTracker, String tableName)
  throws IOException, InterruptedException {
    byte [] tableNameBytes = Bytes.toBytes(tableName);
    if (Bytes.equals(tableNameBytes, HConstants.ROOT_TABLE_NAME)) {
      // If root, do a bit of special handling.
      HServerAddress hsa = catalogTracker.getRootLocation();
      List<Pair<HRegionInfo, HServerAddress>> list =
        new ArrayList<Pair<HRegionInfo, HServerAddress>>();
      list.add(new Pair<HRegionInfo, HServerAddress>(HRegionInfo.ROOT_REGIONINFO, hsa));
      return list;
    }
    HRegionInterface metaServer =
      getCatalogRegionInterface(catalogTracker, tableNameBytes);
    List<Pair<HRegionInfo, HServerAddress>> regions =
      new ArrayList<Pair<HRegionInfo, HServerAddress>>();
    byte[] firstRowInTable = Bytes.toBytes(tableName + ",,");
    Scan scan = new Scan(firstRowInTable);
    scan.addFamily(HConstants.CATALOG_FAMILY);
    long scannerid =
      metaServer.openScanner(getCatalogRegionNameForTable(tableNameBytes), scan);
    try {
      Result data;
      while((data = metaServer.next(scannerid)) != null) {
        if (data != null && data.size() > 0) {
          Pair<HRegionInfo, HServerAddress> region = metaRowToRegionPair(data);
          if (region.getFirst().getTableDesc().getNameAsString().equals(
              tableName)) {
            regions.add(region);
          } else {
            break;
          }
        }
      }
      return regions;
    } finally {
      metaServer.close(scannerid);
    }
  }

  /**
   * @param catalogTracker
   * @param hsi Server specification
   * @return List of user regions installed on this server (does not include
   * catalog regions).
   * @throws IOException
   */
  public static NavigableMap<HRegionInfo, Result>
  getServerUserRegions(CatalogTracker catalogTracker, final HServerInfo hsi)
  throws IOException {
    HRegionInterface metaServer =
      catalogTracker.waitForMetaServerConnectionDefault();
    NavigableMap<HRegionInfo, Result> hris = new TreeMap<HRegionInfo, Result>();
    Scan scan = new Scan();
    scan.addFamily(HConstants.CATALOG_FAMILY);
    long scannerid = metaServer.openScanner(
        HRegionInfo.FIRST_META_REGIONINFO.getRegionName(), scan);
    try {
      Result result;
      while((result = metaServer.next(scannerid)) != null) {
        if (result != null && result.size() > 0) {
          Pair<HRegionInfo, HServerAddress> pair = metaRowToRegionPair(result);
          if (!pair.getSecond().equals(hsi.getServerAddress())) continue;
          hris.put(pair.getFirst(), result);
        }
      }
      return hris;
    } finally {
      metaServer.close(scannerid);
    }
  }
}
