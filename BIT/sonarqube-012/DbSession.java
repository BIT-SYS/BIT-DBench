/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core.persistence;

import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.sonar.core.cluster.ClusterAction;
import org.sonar.core.cluster.WorkQueue;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DbSession implements SqlSession {

  private List<ClusterAction> actions;

  private WorkQueue queue;
  private SqlSession session;
  private int actionCount;

  DbSession(WorkQueue queue, SqlSession session) {
    this.actionCount = 0;
    this.session = session;
    this.queue = queue;
    this.actions = new ArrayList<ClusterAction>();
  }

  public void enqueue(ClusterAction action) {
    actionCount++;
    this.actions.add(action);
  }

  public int getActionCount() {
    return actionCount;
  }

  @Override
  public void commit() {
    session.commit();
    queue.enqueue(actions);
    actions.clear();
  }

  @Override
  public void commit(boolean force) {
    session.commit(force);
    queue.enqueue(actions);
    actions.clear();
  }

  /**
   * We only care about the the commit section.
   * The rest is simply passed to its parent.
   */

  @Override
  public <T> T selectOne(String statement) {
    return session.selectOne(statement);
  }

  @Override
  public <T> T selectOne(String statement, Object parameter) {
    return session.selectOne(statement, parameter);
  }

  @Override
  public <E> List<E> selectList(String statement) {
    return session.selectList(statement);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return session.selectList(statement, parameter);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return session.selectList(statement, parameter, rowBounds);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return session.selectMap(statement, mapKey);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return session.selectMap(statement, parameter, mapKey);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return session.selectMap(statement, parameter, mapKey, rowBounds);
  }

  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    session.select(statement, parameter, handler);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    session.select(statement, handler);
  }

  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    session.select(statement, parameter, rowBounds, handler);
  }

  @Override
  public int insert(String statement) {
    return session.insert(statement);
  }

  @Override
  public int insert(String statement, Object parameter) {
    return session.insert(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return session.update(statement);
  }

  @Override
  public int update(String statement, Object parameter) {
    return session.update(statement, parameter);
  }

  @Override
  public int delete(String statement) {
    return session.delete(statement);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return session.delete(statement, parameter);
  }

  @Override
  public void rollback() {
    session.rollback();
  }

  @Override
  public void rollback(boolean force) {
    session.rollback(force);
  }

  @Override
  public List<BatchResult> flushStatements() {
    return session.flushStatements();
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  public void clearCache() {
    session.clearCache();
  }

  @Override
  public Configuration getConfiguration() {
    return session.getConfiguration();
  }

  @Override
  public <T> T getMapper(Class<T> type) {
    return session.getMapper(type);
  }

  @Override
  public Connection getConnection() {
    return session.getConnection();
  }
}
