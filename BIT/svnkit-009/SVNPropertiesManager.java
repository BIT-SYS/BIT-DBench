/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNPropertiesManager {
    
    private static final Collection NOT_ALLOWED_FOR_FILE = new HashSet();
    private static final Collection NOT_ALLOWED_FOR_DIR = new HashSet();
    
    static {
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.IGNORE);
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.EXTERNALS);
        
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EXECUTABLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.KEYWORDS);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EOL_STYLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.NEEDS_LOCK);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.MIME_TYPE);
    }

    public static boolean setWCProperty(SVNWCAccess access, File path, String propName, SVNPropertyValue propValue,
            boolean write) throws SVNException {
        SVNEntry entry = access.getVersionedEntry(path, false);
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        SVNVersionedProperties wcProps = dir.getWCProperties(entry.getName());
        SVNPropertyValue oldValue = wcProps.getPropertyValue(propName);
        wcProps.setPropertyValue(propName, propValue);
        if (write) {
            dir.saveWCProperties(false);
        }
        return oldValue == null ? propValue != null : !oldValue.equals(propValue);
    }

    public static SVNPropertyValue getWCProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getWCProperties(entry.getName()).getPropertyValue(propName);
    }
    
    public static void deleteWCProperties(SVNAdminArea dir, String name, boolean recursive) throws SVNException {
        if (name != null) {
            SVNVersionedProperties props = dir.getWCProperties(name);
            if (props != null) {
                props.removeAll();
            }
        } 
        if (recursive || name == null) {
            for(Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                SVNVersionedProperties props = dir.getWCProperties(entry.getName());
                if (props != null) {
                    props.removeAll();
                }
                if (entry.isFile() || dir.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                if (recursive) {
                    SVNAdminArea childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
                    deleteWCProperties(childDir, null, true);
                }
            }
        }
        dir.saveWCProperties(false);
    }

    public static SVNPropertyValue getProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        String[] cachableProperties = entry.getCachableProperties();
        if (cachableProperties != null && contains(cachableProperties, propName)) {
            String[] presentProperties = entry.getPresentProperties();
            if (presentProperties == null || !contains(presentProperties, propName)) {
                return null;
            }
            if (SVNProperty.isBooleanProperty(propName)) {
                return SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            return getWCProperty(access, path, propName);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err);
        } 
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getProperties(entry.getName()).getPropertyValue(propName);
    }

    public static boolean setProperty(SVNWCAccess access, File path, String propName, String propValue,
                                      boolean skipChecks) throws SVNException {
        return setProperty(access, path, propName, new SVNPropertyValue(propValue), skipChecks);

    }
    public static boolean setProperty(SVNWCAccess access, File path, String propName, SVNPropertyValue propValue,
            boolean skipChecks) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            return setWCProperty(access, path, propName, propValue, true);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err);
        }
        SVNEntry entry = access.getVersionedEntry(path, false);
        SVNAdminArea dir = entry.getAdminArea();
        boolean updateTimeStamp = SVNProperty.EOL_STYLE.equals(propName);
        if (propValue != null) {
            validatePropertyName(path, propName, entry.getKind());
            if (!skipChecks && SVNProperty.EOL_STYLE.equals(propName)) {
                propValue = propValue.trim();
                validateEOLProperty(path, access);
            } else if (!skipChecks && SVNProperty.MIME_TYPE.equals(propName)) {
                propValue = propValue.trim();
                validateMimeType(propValue.getString());
            } else if (SVNProperty.EXTERNALS.equals(propName) || SVNProperty.IGNORE.equals(propName)) {
                if (!propValue.endsWith("\n")) {
                    propValue = propValue.append("\n");
                }
                if (SVNProperty.EXTERNALS.equals(propName)) {
                    SVNExternal.parseExternals(path.getAbsolutePath(), propValue.getString());
                }
            } else if (SVNProperty.KEYWORDS.equals(propName)) {
                propValue = propValue.trim();
            }
        }
        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.EXECUTABLE.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setExecutable(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
                SVNFileUtil.setExecutable(path, true);
            }
        }
        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.NEEDS_LOCK.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setReadonly(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        SVNVersionedProperties properties = dir.getProperties(entry.getName());
        SVNPropertyValue oldValue = properties.getPropertyValue(propName);
        if (!updateTimeStamp && (entry.getKind() == SVNNodeKind.FILE && SVNProperty.KEYWORDS.equals(propName))) {
            Collection oldKeywords = getKeywords(oldValue.getString());
            Collection newKeywords = getKeywords(propValue == null ? null : propValue.getString());
            updateTimeStamp = !oldKeywords.equals(newKeywords); 
        }
        SVNLog log = dir.getLog();
        if (updateTimeStamp) {
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, entry.getName());
            command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), (String) null);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        }
        properties.setPropertyValue(propName, propValue);
        dir.saveVersionedProperties(log, false);
        log.save();
        dir.runLogs();
        return oldValue == null ? propValue != null : !oldValue.equals(propValue);
    }
    
    public static SVNStatusType mergeProperties(SVNWCAccess wcAccess, File path, SVNProperties baseProperties, SVNProperties diff, boolean baseMerge, boolean dryRun) throws SVNException {
        SVNEntry entry = wcAccess.getVersionedEntry(path, false);
        File parent = null;
        String name = null;
        if (entry.isDirectory()) {
            parent = path;
            name = "";
        } else if (entry.isFile()) {
            parent = path.getParentFile();
            name = entry.getName();
        } 
        
        SVNLog log = null;
        SVNAdminArea dir = wcAccess.retrieve(parent);
        if (!dryRun) {
            log = dir.getLog();            
        }
        SVNStatusType result = dir.mergeProperties(name, baseProperties, diff, baseMerge, dryRun, log);
        if (!dryRun) {
            log.save();
            dir.runLogs();
        }
        return result; 
    }
    
    public static Map computeAutoProperties(ISVNOptions options, File file) {
        Map properties = options.applyAutoProperties(file, null);
        if (!properties.containsKey(SVNProperty.MIME_TYPE)) {
            String mimeType = SVNFileUtil.detectMimeType(file);
            if (mimeType != null) {
                properties.put(SVNProperty.MIME_TYPE, mimeType);
            }
        }
        if (SVNProperty.isBinaryMimeType((String) properties.get(SVNProperty.MIME_TYPE))) {
            properties.remove(SVNProperty.EOL_STYLE);
        }
        if (!properties.containsKey(SVNProperty.EXECUTABLE)) {
            if (SVNFileUtil.isExecutable(file)) {
                properties.put(SVNProperty.EXECUTABLE, SVNProperty.getValueOfBooleanProperty(SVNProperty.EXECUTABLE));
            }
        }
        return properties;
    }
    
    public static Map getWorkingCopyPropertyValues(File path, SVNEntry entry, final String propName, 
            SVNDepth depth, final boolean base) throws SVNException {
        final Map pathsToPropValues = new HashMap();
        
        ISVNEntryHandler handler = new ISVNEntryHandler() {
            public void handleEntry(File path, SVNEntry entry) throws SVNException {
                SVNAdminArea adminArea = entry.getAdminArea();
                if (entry.isDirectory() && !entry.getName().equals(adminArea.getThisDirName())) {
                    return;
                }
                
                if ((entry.isScheduledForAddition() && base) ||
                    (entry.isScheduledForDeletion() && !base)) {
                    return;
                }
                
                SVNPropertyValue propValue = null;
                if (base) {
                    SVNVersionedProperties baseProps = adminArea.getBaseProperties(entry.getName());
                    propValue = baseProps.getPropertyValue(propName);
                } else {
                    SVNVersionedProperties workingProps = adminArea.getProperties(entry.getName());
                    propValue = workingProps.getPropertyValue(propName);
                }
                
                if (propValue != null) {
                    pathsToPropValues.put(path, propValue);
                }
            }
            
            public void handleError(File path, SVNErrorMessage error) throws SVNException {
                while (error.hasChildErrorMessage()) {
                    error = error.getChildErrorMessage();
                }
                if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    return;
                }
                SVNErrorManager.error(error);
            }
        };
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        
        SVNAdminArea adminArea = entry.getAdminArea();
        if (entry.isDirectory() && depth.compareTo(SVNDepth.FILES) >= 0) {
            SVNWCAccess wcAccess = adminArea.getWCAccess(); 
            wcAccess.walkEntries(path, handler, false, depth);    
        } else {
            handler.handleEntry(path, entry);
        }
        
        return pathsToPropValues;
    }

    public static void recordWCMergeInfo(File path, Map mergeInfo, SVNWCAccess wcAccess) throws SVNException {
        String value = null;
        if (mergeInfo != null) {
            value = SVNMergeInfoManager.formatMergeInfoToString(mergeInfo); 
        }
        setProperty(wcAccess, path, SVNProperty.MERGE_INFO, value, true);
    }
    
    public static Map parseMergeInfo(File path, SVNEntry entry, boolean base) throws SVNException {
        Map fileToProp = SVNPropertiesManager.getWorkingCopyPropertyValues(path, entry, SVNProperty.MERGE_INFO, 
                SVNDepth.EMPTY, base); 

        Map result = null;
        String propValue = (String) fileToProp.get(path);
        if (propValue != null) {
            result = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(propValue), result);
        }
        return result;
    }
    
    public static boolean isValidPropertyName(String name) throws SVNException {
        if (name == null || name.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
            "Property name is empty");
            SVNErrorManager.error(err);
        }

        if (!(Character.isLetter(name.charAt(0)) || name.charAt(0) == ':' || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!(Character.isLetterOrDigit(name.charAt(i))
                    || name.charAt(i) == '-' || name.charAt(i) == '.'
                    || name.charAt(i) == ':' || name.charAt(i) == '_')) {
                return false;
            }
        }
        return true;
    }

    public static boolean propNeedsTranslation(String propertyName){
        return SVNProperty.isSVNProperty(propertyName);
    }

    private static void validatePropertyName(File path, String name, SVNNodeKind kind) throws SVNException {
        SVNErrorMessage err = null;
        if (kind == SVNNodeKind.DIR) {
            if (NOT_ALLOWED_FOR_DIR.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a directory (''{1}'')", new Object[] {name, path});
            }
        } else if (kind == SVNNodeKind.FILE) {
            if (NOT_ALLOWED_FOR_FILE.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a file (''{1}'')", new Object[] {name, path});
            }
        } else {
            err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is not a file or directory", path);
        }
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }

    private static void validateEOLProperty(File path, SVNWCAccess access) throws SVNException {
        SVNPropertyValue mimeType = getProperty(access, path, SVNProperty.MIME_TYPE);
        if (mimeType != null && SVNProperty.isBinaryMimeType(mimeType.getString())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", path);
            SVNErrorManager.error(err);
        }
        boolean consistent = SVNTranslator.checkNewLines(path);
        if (!consistent) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has inconsistent newlines", path);
            SVNErrorManager.error(err);
        }
    }

    private static void validateMimeType(String value) throws SVNException {
        String type = value.indexOf(';') >= 0 ? value.substring(0, value.indexOf(';')) : value;
        SVNErrorMessage err = null;
        if (type.length() == 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' has empty media type", value);
        } else if (type.indexOf('/') < 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' does not contain ''/''", value);
        } else if (!Character.isLetterOrDigit(type.charAt(type.length() - 1))) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' ends with non-alphanumeric character", value);
        }
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }
    
    private static Collection getKeywords(String value) {
        Collection keywords = new HashSet();
        if (value == null || "".equals(value.trim())) {
            return keywords;
        }
        for(StringTokenizer tokens = new StringTokenizer(value, " \t\n\r"); tokens.hasMoreTokens();) {
            keywords.add(tokens.nextToken().toLowerCase());
        }
        return keywords;
    }
    
    private static boolean contains(String[] values, String value) {
        for (int i = 0; value != null && i < values.length; i++) {
            if (values[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

}
