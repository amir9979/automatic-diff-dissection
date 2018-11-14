 package org.apache.cassandra.db.marshal;
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
 
 
 import java.nio.ByteBuffer;
 import java.util.Collection;
 import java.util.Comparator;
 
 import org.apache.cassandra.db.IColumn;
 
 /**
  * Specifies a Comparator for a specific type of ByteBuffer.
  *
  * Note that empty ByteBuffer are used to represent "start at the beginning"
  * or "stop at the end" arguments to get_slice, so the Comparator
  * should always handle those values even if they normally do not
  * represent a valid ByteBuffer for the type being compared.
  */
 public abstract class AbstractType implements Comparator<ByteBuffer>
 {
     /** get a string representation of the bytes suitable for log messages */
     public abstract String getString(ByteBuffer bytes);
 
     /** get a byte representation of the given string.
      *  defaults to unsupportedoperation so people deploying custom Types can update at their leisure. */
     public ByteBuffer fromString(String source)
     {
         throw new UnsupportedOperationException();
     }
 
     /* validate that the byte array is a valid sequence for the type we are supposed to be comparing */
    public void validate(ByteBuffer bytes)
    {
        getString(bytes);
    }
 
     public Comparator<ByteBuffer> getReverseComparator()
     {
         return new Comparator<ByteBuffer>()
         {
             public int compare(ByteBuffer o1, ByteBuffer o2)
             {
                 if (o1.remaining() == 0)
                 {
                     return o2.remaining() == 0 ? 0 : -1;
                 }
                 if (o2.remaining() == 0)
                 {
                     return 1;
                 }
 
                 return -AbstractType.this.compare(o1, o2);
             }
         };
     }
 
     /* convenience method */
     public String getString(Collection<ByteBuffer> names)
     {
         StringBuilder builder = new StringBuilder();
         for (ByteBuffer name : names)
         {
             builder.append(getString(name)).append(",");
         }
         return builder.toString();
     }
 
     /* convenience method */
     public String getColumnsString(Collection<IColumn> columns)
     {
         StringBuilder builder = new StringBuilder();
         for (IColumn column : columns)
         {
             builder.append(column.getString(this)).append(",");
         }
         return builder.toString();
     }
 }
