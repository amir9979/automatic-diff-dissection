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
 
 package org.apache.mahout.vectors;
 
 import org.apache.mahout.math.Vector;
 
 import java.util.Locale;
 
 public class InteractionValueEncoder extends FeatureVectorEncoder {
 
   protected static final int INTERACTION_VALUE_HASH_SEED_1 = 100;
   protected static final int INTERACTION_VALUE_HASH_SEED_2 = 200;
 
    public InteractionValueEncoder(String name) {
        super(name, 2);
      }
 
   /**
    * Adds a value to a vector.
    *
    * @param originalForm The original form of the first value as a string.
    * @param data         The vector to which the value should be added.
    */
   @Override
   public void addToVector(String originalForm, double w, Vector data) {
   }
 
      /**
       * Adds a value to a vector.
       *
       * @param originalForm1 The original form of the first value as a string.
       * @param originalForm2 The original form of the second value as a string.
       * @param data          The vector to which the value should be added.
       */
     public void addInteractionToVector(String originalForm1, String originalForm2, Vector data) {
        int probes = getProbes();
        String name = getName();
        for (int i = 0; i < probes; i++) {
         int h1 = hash1(name, originalForm1, i, data.size());
         int h2 = hash2(name, originalForm1, i, data.size());
         int j =  hash1(name, originalForm2, i, data.size());
          int n = (h1 + (j+1)*h2) % data.size();
          if(n < 0){
              n = n+data.size();
          }
          trace(String.format("%s:%s", originalForm1, originalForm2), n);
         data.set(n, data.get(n) + 1);
        }
      }
 
   /**
   * Converts a value into a form that would help a human understand the internals of how the
   * value is being interpreted.  For text-like things, this is likely to be a list of the terms
   * found with associated weights (if any).
    *
    * @param originalForm The original form of the value as a string.
    * @return A string that a human can read.
    */
   @Override
   public String asString(String originalForm) {
    return String.format(Locale.ENGLISH, "%s:%s", getName(), originalForm);
   }
 
   protected int hash1(String term1, String term2, int probe, int numFeatures) {
    return hash(term1, term2, probe + INTERACTION_VALUE_HASH_SEED_1, numFeatures);
   }
 
   protected int hash2(String term1, String term2, int probe, int numFeatures) {
    return hash(term1, term2, probe + INTERACTION_VALUE_HASH_SEED_2, numFeatures);
   }
 }
 
