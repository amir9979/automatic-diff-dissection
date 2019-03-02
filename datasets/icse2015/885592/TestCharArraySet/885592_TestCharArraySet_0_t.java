 package org.apache.lucene.analysis;
 
 /**
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
 
 import java.util.Arrays;
 
 import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.Version;
 
 public class TestCharArraySet extends LuceneTestCase {
   
   static final String[] TEST_STOP_WORDS = {
     "a", "an", "and", "are", "as", "at", "be", "but", "by",
     "for", "if", "in", "into", "is", "it",
     "no", "not", "of", "on", "or", "such",
     "that", "the", "their", "then", "there", "these",
     "they", "this", "to", "was", "will", "with"
   };
   
   
   public void testRehash() throws Exception {
    CharArraySet cas = new CharArraySet(Version.LUCENE_CURRENT, 0, true);
     for(int i=0;i<TEST_STOP_WORDS.length;i++)
       cas.add(TEST_STOP_WORDS[i]);
     assertEquals(TEST_STOP_WORDS.length, cas.size());
     for(int i=0;i<TEST_STOP_WORDS.length;i++)
       assertTrue(cas.contains(TEST_STOP_WORDS[i]));
   }
 
   public void testNonZeroOffset() {
     String[] words={"Hello","World","this","is","a","test"};
     char[] findme="xthisy".toCharArray();   
    CharArraySet set=new CharArraySet(Version.LUCENE_CURRENT, 10,true);
     set.addAll(Arrays.asList(words));
     assertTrue(set.contains(findme, 1, 4));
     assertTrue(set.contains(new String(findme,1,4)));
     
     // test unmodifiable
     set = CharArraySet.unmodifiableSet(set);
     assertTrue(set.contains(findme, 1, 4));
     assertTrue(set.contains(new String(findme,1,4)));
   }
   
   public void testObjectContains() {
    CharArraySet set = new CharArraySet(Version.LUCENE_CURRENT, 10, true);
     Integer val = Integer.valueOf(1);
     set.add(val);
     assertTrue(set.contains(val));
     assertTrue(set.contains(Integer.valueOf(1)));
     // test unmodifiable
     set = CharArraySet.unmodifiableSet(set);
     assertTrue(set.contains(val));
     assertTrue(set.contains(Integer.valueOf(1)));
   }
   
   public void testClear(){
    CharArraySet set=new CharArraySet(Version.LUCENE_CURRENT, 10,true);
     set.addAll(Arrays.asList(TEST_STOP_WORDS));
     assertEquals("Not all words added", TEST_STOP_WORDS.length, set.size());
     try{
       set.clear();
       fail("remove is not supported");
     }catch (UnsupportedOperationException e) {
       // expected
       assertEquals("Not all words added", TEST_STOP_WORDS.length, set.size());
     }
   }
   
   public void testModifyOnUnmodifiable(){
    CharArraySet set=new CharArraySet(Version.LUCENE_CURRENT, 10,true);
     set.addAll(Arrays.asList(TEST_STOP_WORDS));
     final int size = set.size();
     set = CharArraySet.unmodifiableSet(set);
     assertEquals("Set size changed due to unmodifiableSet call" , size, set.size());
     String NOT_IN_SET = "SirGallahad";
     assertFalse("Test String already exists in set", set.contains(NOT_IN_SET));
     
     try{
       set.add(NOT_IN_SET.toCharArray());  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     
     try{
       set.add(NOT_IN_SET);  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     
     try{
       set.add(new StringBuilder(NOT_IN_SET));  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     
     try{
       set.clear();  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Changed unmodifiable set", set.contains(NOT_IN_SET));
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     try{
       set.add((Object) NOT_IN_SET);  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     try{
       set.removeAll(Arrays.asList(TEST_STOP_WORDS));  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     
     try{
       set.retainAll(Arrays.asList(new String[]{NOT_IN_SET}));  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertEquals("Size of unmodifiable set has changed", size, set.size());
     }
     
     try{
       set.addAll(Arrays.asList(new String[]{NOT_IN_SET}));  
       fail("Modified unmodifiable set");
     }catch (UnsupportedOperationException e) {
       // expected
       assertFalse("Test String has been added to unmodifiable set", set.contains(NOT_IN_SET));
     }
     
     for (int i = 0; i < TEST_STOP_WORDS.length; i++) {
       assertTrue(set.contains(TEST_STOP_WORDS[i]));  
     }
   }
   
   public void testUnmodifiableSet(){
    CharArraySet set = new CharArraySet(Version.LUCENE_CURRENT, 10,true);
     set.addAll(Arrays.asList(TEST_STOP_WORDS));
     final int size = set.size();
     set = CharArraySet.unmodifiableSet(set);
     assertEquals("Set size changed due to unmodifiableSet call" , size, set.size());
     
     try{
       CharArraySet.unmodifiableSet(null);
       fail("can not make null unmodifiable");
     }catch (NullPointerException e) {
       // expected
     }
   }
  
  public void testSupplementaryChars() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    // for reference see
    // http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[[%3ACase_Sensitive%3DTrue%3A]%26[^[\u0000-\uFFFF]]]&esc=on
    String[] upperArr = new String[] {"Abc\ud801\udc1c",
        "\ud801\udc1c\ud801\udc1cCDE", "A\ud801\udc1cB"};
    String[] lowerArr = new String[] {"abc\ud801\udc44",
        "\ud801\udc44\ud801\udc44cde", "a\ud801\udc44b"};
    CharArraySet set = new CharArraySet(Version.LUCENE_31, Arrays.asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertTrue(String.format(missing, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_31, Arrays.asList(TEST_STOP_WORDS), false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
  }
  
  public void testSingleHighSurrogate() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    String[] upperArr = new String[] { "ABC\uD800", "ABC\uD800EfG",
        "\uD800EfG", "\uD800\ud801\udc1cB" };

    String[] lowerArr = new String[] { "abc\uD800", "abc\uD800efg",
        "\uD800efg", "\uD800\ud801\udc44b" };
    CharArraySet set = new CharArraySet(Version.LUCENE_31, Arrays
        .asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertTrue(String.format(missing, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_31, Arrays.asList(TEST_STOP_WORDS),
        false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, upperArr[i]), set
          .contains(lowerArr[i]));
    }
  }
  
  /**
   * @deprecated remove this test when lucene 3.0 "broken unicode 4" support is
   *             no longer needed.
   */
  public void testSupplementaryCharsBWCompat() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    // for reference see
    // http://unicode.org/cldr/utility/list-unicodeset.jsp?a=[[%3ACase_Sensitive%3DTrue%3A]%26[^[\u0000-\uFFFF]]]&esc=on
    String[] upperArr = new String[] {"Abc\ud801\udc1c",
        "\ud801\udc1c\ud801\udc1cCDE", "A\ud801\udc1cB"};
    String[] lowerArr = new String[] {"abc\ud801\udc44",
        "\ud801\udc44\ud801\udc44cde", "a\ud801\udc44b"};
    CharArraySet set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS), false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set.contains(lowerArr[i]));
    }
  }

  /**
   * @deprecated remove this test when lucene 3.0 "broken unicode 4" support is
   *             no longer needed.
   */
  public void testSingleHighSurrogateBWComapt() {
    String missing = "Term %s is missing in the set";
    String falsePos = "Term %s is in the set but shouldn't";
    String[] upperArr = new String[] { "ABC\uD800", "ABC\uD800EfG",
        "\uD800EfG", "\uD800\ud801\udc1cB" };

    String[] lowerArr = new String[] { "abc\uD800", "abc\uD800efg",
        "\uD800efg", "\uD800\ud801\udc44b" };
    CharArraySet set = new CharArraySet(Version.LUCENE_30, Arrays
        .asList(TEST_STOP_WORDS), true);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      if (i == lowerArr.length - 1)
        assertFalse(String.format(falsePos, lowerArr[i]), set
            .contains(lowerArr[i]));
      else
        assertTrue(String.format(missing, lowerArr[i]), set
            .contains(lowerArr[i]));
    }
    set = new CharArraySet(Version.LUCENE_30, Arrays.asList(TEST_STOP_WORDS),
        false);
    for (String upper : upperArr) {
      set.add(upper);
    }
    for (int i = 0; i < upperArr.length; i++) {
      assertTrue(String.format(missing, upperArr[i]), set.contains(upperArr[i]));
      assertFalse(String.format(falsePos, lowerArr[i]), set
          .contains(lowerArr[i]));
    }
  }
 }
