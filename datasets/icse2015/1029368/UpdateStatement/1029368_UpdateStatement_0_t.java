 /*
  * 
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  * 
  *   http://www.apache.org/licenses/LICENSE-2.0
  * 
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
  * 
  */
package org.apache.cassandra.cql;
 
 import java.util.ArrayList;
 import java.util.List;
 import org.apache.cassandra.thrift.ConsistencyLevel;
 
/**
 * An <code>UPDATE</code> statement parsed from a CQL query statement.
 *
 */
 public class UpdateStatement
 {
     private String columnFamily;
     private List<Row> rows = new ArrayList<Row>();
     private ConsistencyLevel cLevel;
     
    /**
     * Creates a new UpdateStatement from a column family name, a row definition,
     * and a consistency level.
     * 
     * @param columnFamily column family name
     * @param first a row definition instance
     * @param cLevel the thrift consistency level
     */
     public UpdateStatement(String columnFamily, Row first, ConsistencyLevel cLevel)
     {
         this.columnFamily = columnFamily;
         this.cLevel = cLevel;
         and(first);
     }
     
    /**
     * Adds a new row definition to this <code>UPDATE</code>.
     * 
     * @param row the row definition to add.
     */
     public void and(Row row)
     {
         rows.add(row);
     }
 
     public List<Row> getRows()
     {
         return rows;
     }
 
     public ConsistencyLevel getConsistencyLevel()
     {
         return cLevel;
     }
 
     public String getColumnFamily()
     {
         return columnFamily;
     }
    
    public String toString()
    {
        return "UpdateStatement(columnFamily=" + columnFamily + ", " +
            "row=" + rows + ", " + "consistency=" + cLevel + ")";
    }
 }
