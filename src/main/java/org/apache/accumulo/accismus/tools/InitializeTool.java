/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.accismus.tools;

import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.accumulo.accismus.api.Column;
import org.apache.accumulo.accismus.api.Operations;
import org.apache.accumulo.accismus.api.config.AccismusProperties;
import org.apache.accumulo.accismus.impl.Configuration;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.accumulo.core.zookeeper.ZooUtil;
import org.apache.accumulo.fate.zookeeper.ZooUtil.NodeMissingPolicy;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooKeeper;

/**
 * 
 */
public class InitializeTool extends Configured implements Tool {
  public static void main(String[] args) throws Exception {
    ToolRunner.run(new InitializeTool(), args);
  }
  
  @Override
  public int run(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage : " + InitializeTool.class.getSimpleName() + " <props file>  <init file>");
      System.exit(-1);
    }
    
    Properties props = new Properties(Configuration.getDefaultProperties());
    props.load(new FileReader(args[0]));
    
    Connector conn = new ZooKeeperInstance(props.getProperty(AccismusProperties.ACCUMULO_INSTANCE_PROP), props.getProperty(AccismusProperties.ZOOKEEPER_CONNECT_PROP))
        .getConnector(
        props.getProperty(AccismusProperties.ACCUMULO_USER_PROP), new PasswordToken(props.getProperty(AccismusProperties.ACCUMULO_PASSWORD_PROP)));
    
    
    Properties initProps = new Properties();
    initProps.load(new FileReader(args[1]));
    
    Map<Column,String> colObservers = new HashMap<Column,String>();
    
    Properties workerConfig = new Properties();

    Set<Entry<Object,Object>> entries = initProps.entrySet();
    for (Entry<Object,Object> entry : entries) {
      String key = (String) entry.getKey();
      if (key.startsWith("accismus.config.observer.")) {
        String val = (String) entry.getValue();
        String[] fields = val.split(",");
        Column col = new Column(fields[0], fields[1]).setVisibility(new ColumnVisibility(fields[2]));
        colObservers.put(col, fields[3]);
      } else if (key.startsWith("accismus.config.worker")) {
        workerConfig.setProperty((String) entry.getKey(), (String) entry.getValue());
      }
    }
    
    if (Boolean.valueOf(initProps.getProperty("accismus.init.zookeeper.clear", "false"))) {
      ZooKeeper zk = new ZooKeeper(props.getProperty(AccismusProperties.ZOOKEEPER_CONNECT_PROP), 30000, null);
      ZooUtil.recursiveDelete(zk, props.getProperty(AccismusProperties.ZOOKEEPER_ROOT_PROP), NodeMissingPolicy.SKIP);
      zk.close();
    }

    try {
      Operations.initialize(conn, props.getProperty(AccismusProperties.ZOOKEEPER_ROOT_PROP), initProps.getProperty("accismus.init.table"), colObservers);
    } catch (NodeExistsException nee) {
      Operations.updateObservers(conn, props.getProperty(AccismusProperties.ZOOKEEPER_ROOT_PROP), colObservers);
      Operations.updateWorkerConfig(conn, props.getProperty(AccismusProperties.ZOOKEEPER_ROOT_PROP), workerConfig);
    }

    // TODO add accismus version to context name to make it unique
    String contextName = "accismus";
    
    conn.instanceOperations().setProperty(Property.VFS_CONTEXT_CLASSPATH_PROPERTY.getKey() + "accismus",
        initProps.getProperty("accismus.init.accumulo.classpath"));
    conn.tableOperations().setProperty(initProps.getProperty("accismus.init.table"), Property.TABLE_CLASSPATH.getKey(), contextName);

    return 0;
  }
}