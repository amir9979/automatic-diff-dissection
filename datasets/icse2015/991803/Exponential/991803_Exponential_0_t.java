 /*
 Copyright ï¿½ 1999 CERN - European Organization for Nuclear Research.
 Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose 
 is hereby granted without fee, provided that the above copyright notice appear in all copies and 
 that both that copyright notice and this permission notice appear in supporting documentation. 
 CERN makes no representations about the suitability of this software for any purpose. 
 It is provided "as is" without expressed or implied warranty.
 */
 package org.apache.mahout.math.jet.random;
 
 import org.apache.mahout.math.jet.random.engine.RandomEngine;
 
import java.util.Locale;
 
public class Exponential extends AbstractContinousDistribution {
  // rate parameter for the distribution.  Mean is 1/lambda.
   private double lambda;
 
  /**
   * Provides a negative exponential distribution given a rate parameter lambda and an underlying
   * random number generator.  The mean of this distribution will be equal to 1/lambda.
   *
   * @param lambda          The rate parameter of the distribution.
   * @param randomGenerator The PRNG that is used to generate values.
   */
   public Exponential(double lambda, RandomEngine randomGenerator) {
     setRandomGenerator(randomGenerator);
     setState(lambda);
   }
 
  /**
   * Returns the cumulative distribution function.
   * @param x  The point at which the cumulative distribution function is to be evaluated.
   * @return Returns the integral from -infinity to x of the PDF, also known as the cumulative distribution
   * function.
   */
   public double cdf(double x) {
     if (x <= 0.0) {
       return 0.0;
     }
     return 1.0 - Math.exp(-x * lambda);
   }
 
  /**
   * Returns a random number from the distribution.
   */
   @Override
   public double nextDouble() {
    return -Math.log(1 - randomGenerator.raw()) / lambda;
   }
 
  /**
   * Returns the value of the probability density function at a particular point.
   * @param x   The point at which the probability density function is to be evaluated.
   * @return  The value of the probability density function at the specified point.
   */
   public double pdf(double x) {
     if (x < 0.0) {
       return 0.0;
     }
     return lambda * Math.exp(-x * lambda);
   }
 
  /**
   * Sets the rate parameter.
   * @param lambda  The new value of the rate parameter.
   */
   public void setState(double lambda) {
     this.lambda = lambda;
   }
 
  /**
   * Returns a String representation of the receiver.
   */
   public String toString() {
    return String.format(Locale.ENGLISH, "%s(%.4f)", this.getClass().getName(), lambda);
   }
 
 }
