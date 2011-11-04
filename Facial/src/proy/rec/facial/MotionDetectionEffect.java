package proy.rec.facial;
/*
 * Author: Konrad Rzeszutek <konrad AT darnok DOT org>
 *
 *
 *
 *
 *    This file is part of Motion Detection toolkit.
 *
 *    Motion Detection toolkit is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    Motion Detection toolkit is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with Foobar; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */


import javax.media.*;
import javax.media.format.*;
import java.awt.*;
/**
 * Motion detection effect.
 *
 * The engine has two steps.
 * First the input image and the
 * reference image (which is the image from the previous frame) is compared.
 * Whenever a pixel has changed consideribly (determined by the
 * <b>threshold</b> variable) a internal black-white-red image is
 * marked (at the same exact location where the change occured). Therefore in
 * the first step, the internal black-white-red image is has lit up clusters
 * in the space where a change has occured.
 * <p>The next step is to eliminate these clusters that are too small, but still
 * appeared in our black-white-red image. Only the big clusters are left (and
 * are colored red). During this process we keep a track of couunt of the big
 * clusters.
 * If the count is greater than <b>blob_threshold</b> then the input frame is
 * determined to have consideribly motion as to the previous frame.
 *
 * <br><br>Many of the ideas have been taken from
 * <ol>
 *  <li><a href="http://gspy.sourceforge.net/">A Gnome Security Camera</a> by
 *  Lawrance P. Glaister.
 *  <li>Digital Image Processing by Kenneth R. Castleman; ISBN 0-13-211467-4
 *  <li>Computer Graphics Principles and Practice by Foley, van Dam, Feiner,
 *  Hughes; ISBN 0-201-84840-6
 *  <li>Java Media Format Sample Programs (mainly the one dealing with
 *     building an Effect plugin)
 * </ol>
 *
 * <br><br>
 * @version: $Id: MotionDetectionEffect.java,v 1.4 2002/04/28 19:14:11 uid500 Exp $
 * @author: Konrad Rzeszutek <konrad@darnok.org>
 *
 */
public class MotionDetectionEffect implements Effect {


  /**
   * Optimization. Anything above 0 turns it on. By default its
   * disabled.
   */
    public int OPTIMIZATION = 0;
    /**
     * Maximum threshold setting. Setting the threshold above this
     * means to get the motion detection to pass the frame you pretty
     * much have to full the whole frame with lots of motions (ie: drop
     * the camera)
     */
    public int THRESHOLD_MAX = 10000;

    /**
     * By what value you should increment.
     */
    public int THRESHOLD_INC = 1000;

    /**
     * The initial threshold setting.
     */
    public int THRESHOLD_INIT = 5000;



    private Format inputFormat;
    private Format outputFormat;
    private Format[] inputFormats;
    private Format[] outputFormats;

    private byte[] refData;
    private byte[] bwData;

    private int avg_ref_intensity;
    private int avg_img_intensity;

    /**
     * The threshold for determing if the pixel at a certain location has
     * changed consideribly.
     */
    public int threshold = 30;
    /**
     * Our threshold for determinig if the input image has enough motion.
     *
     */
    public int blob_threshold = THRESHOLD_INIT;

    /**
     * Turn debugging on. Slows down the effect but shows how motion
     * detection effect works.
     */
    public boolean debug = false;

    /**
     * Initialize the effect plugin.
     */
    public MotionDetectionEffect() {

        inputFormats = new Format[] {
            new RGBFormat(null,
                          Format.NOT_SPECIFIED,
                          Format.byteArray,
                          Format.NOT_SPECIFIED,
                          24,
                          3, 2, 1,
                          3, Format.NOT_SPECIFIED,
                          Format.TRUE,
                          Format.NOT_SPECIFIED)
        };

        outputFormats = new Format[] {
            new RGBFormat(null,
                          Format.NOT_SPECIFIED,
                          Format.byteArray,
                          Format.NOT_SPECIFIED,
                          24,
                          3, 2, 1,
                          3, Format.NOT_SPECIFIED,
                          Format.TRUE,
                          Format.NOT_SPECIFIED)
        };

    }

    /**
     * Get the format that we support
     *
     * @return  instanceof RGBFormat
     */
    public Format[] getSupportedInputFormats() {
	return inputFormats;
    }

    /**
     * Get the format that we support
     *
     * @return  instanceof RGBFormat
     */
    public Format [] getSupportedOutputFormats(Format input) {
        if (input == null) {
            return outputFormats;
        }

        if (matches(input, inputFormats) != null) {
            return new Format[] { outputFormats[0].intersects(input) };
        } else {
            return new Format[0];
        }
    }

    /**
     * Set our input format.
     */
    public Format setInputFormat(Format input) {

	inputFormat = input;
	return input;
    }

    /**
     * Set our output format.
     *
     */
    public Format setOutputFormat(Format output) {

        if (output == null || matches(output, outputFormats) == null)
            return null;

        RGBFormat incoming = (RGBFormat) output;

        Dimension size = incoming.getSize();
        int maxDataLength = incoming.getMaxDataLength();
        int lineStride = incoming.getLineStride();
        float frameRate = incoming.getFrameRate();
        int flipped = incoming.getFlipped();
        int endian = incoming.getEndian();

        if (size == null)
            return null;
        if (maxDataLength < size.width * size.height * 3)
            maxDataLength = size.width * size.height * 3;
        if (lineStride < size.width * 3)
            lineStride = size.width * 3;
        if (flipped != Format.FALSE)
            flipped = Format.FALSE;

        outputFormat = outputFormats[0].intersects(new RGBFormat(size,
                                                        maxDataLength,
                                                        null,
                                                        frameRate,
                                                        Format.NOT_SPECIFIED,
                                                        Format.NOT_SPECIFIED,
                                                        Format.NOT_SPECIFIED,
                                                        Format.NOT_SPECIFIED,
                                                        Format.NOT_SPECIFIED,
                                                        lineStride,
                                                        Format.NOT_SPECIFIED,
                                                        Format.NOT_SPECIFIED));

        return outputFormat;
    }

   /**
    */

    public int process(Buffer inBuffer, Buffer outBuffer) {

      /*
      optimization ideas:
       first scale down the image.
       convert the image to an int[][] array (instead of using byte[][])

       then do all the calculation on int[][] array instead of
       masking the bit.

       furthermore, only do the comparison every 5 frames instead of every frame.


      */
        int outputDataLength = ((VideoFormat)outputFormat).getMaxDataLength();

        validateByteArraySize(outBuffer, outputDataLength);

        outBuffer.setLength(outputDataLength);
        outBuffer.setFormat(outputFormat);
        outBuffer.setFlags(inBuffer.getFlags());

        byte [] inData = (byte[]) inBuffer.getData();
        byte [] outData = (byte[]) outBuffer.getData();

        RGBFormat vfIn = (RGBFormat) inBuffer.getFormat();
        Dimension sizeIn = vfIn.getSize();

        int pixStrideIn = vfIn.getPixelStride();
        int lineStrideIn = vfIn.getLineStride();

        int y, x;
        int width = sizeIn.width;
        int height = sizeIn.height;
        int r,g,b;
        int ip, op;
        byte result;
        int avg = 0;
        int refDataInt = 0;
        int inDataInt = 0;
        int correction;
        int blob_cnt = 0;


        if (refData == null) {
          refData = new byte[outputDataLength];
          bwData = new byte[outputDataLength];

          System.arraycopy (inData,0,refData,0,inData.length);
          System.arraycopy(inData,0,outData,0,inData.length);

      	  for (ip  = 0; ip < outputDataLength; ip++) {
        	avg += (int) (refData[ip] & 0xFF);
      	  }

      	  avg_ref_intensity =  avg / outputDataLength;
          return BUFFER_PROCESSED_OK;
        }

        if ( outData.length < sizeIn.width*sizeIn.height*3 ) {
            System.out.println("the buffer is not full");
            return BUFFER_PROCESSED_FAILED;
        }

        for (ip  = 0; ip < outputDataLength; ip++) {
          avg += (int) (inData[ip] & 0xFF);
        }

      avg_img_intensity = avg / outputDataLength;
      correction = (avg_ref_intensity < avg_img_intensity) ?
      avg_img_intensity - avg_ref_intensity :
      avg_ref_intensity - avg_img_intensity;
//      System.out.println(avg_ref_intensity + "; "+avg_img_intensity+" = "+correction);
//
      avg_ref_intensity = avg_img_intensity;
      ip = op = 0;

      /**
       * This is comparng the last frame with the new frame.
       * We lit up only the pixels which changed, the rest is discarded (on our
       * b/w image - used for determing the motion
       *
       */
      for (int ii=0; ii< outputDataLength/pixStrideIn; ii++) {

          refDataInt = (int) refData[ip] & 0xFF;
          inDataInt = (int) inData[ip++] & 0xFF;
          r =  (refDataInt > inDataInt) ? refDataInt - inDataInt : inDataInt - refDataInt;

          refDataInt = (int) refData[ip] & 0xFF;
          inDataInt = (int) inData[ip++] & 0xFF;
          g =  (refDataInt > inDataInt) ? refDataInt - inDataInt : inDataInt - refDataInt;

          refDataInt = (int) refData[ip] & 0xFF;
          inDataInt = (int) inData[ip++] & 0xFF;
          b =  (refDataInt > inDataInt) ? refDataInt - inDataInt : inDataInt - refDataInt;

          // intensity normalization
          r -= (r < correction) ? r : correction;
          g -= (g < correction) ? g : correction;
          b -= (b < correction) ? b : correction;

          result = (byte)(java.lang.Math.sqrt((double)( (r*r) + (g*g) + (b*b) ) / 3.0));
          /*
            black/white image now.
          */
	  if (result > (byte)threshold) {
             bwData[op++] = (byte)255;
             bwData[op++] = (byte)255;
             bwData[op++] = (byte)255;
       	  }  else {
	     bwData[op++] = (byte)result;
             bwData[op++] = (byte)result;
             bwData[op++] = (byte)result;
	   }
      }

      // blob elimination
      for (op = lineStrideIn + 3; op < outputDataLength - lineStrideIn-3; op+=3) {
        for (int i=0; i<1; i++) {
          if (((int)bwData[op+2] & 0xFF) < 255) break;
          if (((int)bwData[op+2-lineStrideIn] & 0xFF) < 255) break;
          if (((int)bwData[op+2+lineStrideIn] & 0xFF) < 255) break;
          if (((int)bwData[op+2-3] & 0xFF) < 255) break;
          if (((int)bwData[op+2+3] & 0xFF) < 255) break;
          if (((int)bwData[op+2-lineStrideIn + 3] & 0xFF) < 255) break;
          if (((int)bwData[op+2-lineStrideIn - 3] & 0xFF) < 255) break;
          if (((int)bwData[op+2+lineStrideIn - 3] & 0xFF) < 255) break;
          if (((int)bwData[op+2+lineStrideIn + 3] & 0xFF) < 255) break;
          bwData[op]  = (byte)0;
          bwData[op+1] = (byte)0;
          blob_cnt ++;
        }
      }


       // when we are finished with comparison we do this.
    if (blob_cnt > blob_threshold) {

	    if (debug) {
     		sample_down(inData,outData, 0, 0,sizeIn.width, sizeIn.height,
                  lineStrideIn, pixStrideIn);
     		Font.println("original picture", Font.FONT_8x8, 0, 0,
                  (byte)255,(byte)255,(byte)255, outBuffer);
     		sample_down(refData,outData, 0, sizeIn.height/2,sizeIn.width,
                   sizeIn.height, lineStrideIn, pixStrideIn);
     		Font.println("reference picture", Font.FONT_8x8, 0,
                  sizeIn.height , (byte)255,(byte)255,(byte)255, outBuffer);
     		sample_down(bwData,outData, sizeIn.width/2, 0,sizeIn.width,
                   sizeIn.height, lineStrideIn, pixStrideIn);
     		Font.println("motion detection pic", Font.FONT_8x8,
                  sizeIn.width/2, 0 , (byte)255,(byte)255,(byte)255, outBuffer);
	    } else
      		System.arraycopy(inData,0,outData,0,inData.length);

       System.arraycopy(inData,0,refData,0,inData.length);
       return BUFFER_PROCESSED_OK;
    }
     return BUFFER_PROCESSED_FAILED;
    }

    // methods for interface PlugIn
    public String getName() {
        return "Motion Detection Codec";
    }

    public void open() {
    }

    public void close() {
    }

    public void reset() {
    }

    // methods for interface javax.media.Controls
    public Object getControl(String controlType) {
        System.out.println(controlType);
	return null;
    }
    private Control[] controls;


    public Object[] getControls() {
      if (controls == null) {
        controls = new Control[1];
        controls[0] = new MotionDetectionControl(this);
      }
      return (Object[])controls;
    }


    // Utility methods.
    Format matches(Format in, Format outs[]) {
	for (int i = 0; i < outs.length; i++) {
	    if (in.matches(outs[i]))
		return outs[i];
	}

	return null;
    }


    void sample_down(byte[] inData, byte[] outData, int X, int Y, int width,
    int height, int lineStrideIn, int pixStrideIn) {

          int p1, p2, p3, p4, op,x,y;

      for ( y = 0; y < (height/2); y++) {

        p1 = (y * 2) * lineStrideIn ; // upper left cell
        p2 = p1 + pixStrideIn;                    // upper right cell
        p3 = p1 + lineStrideIn;         // lower left cell
        p4 = p3 + pixStrideIn;                    // lower right cell
        op = lineStrideIn * y + (lineStrideIn*Y) + (X*pixStrideIn);
        for ( int i =0; i< (width /2 );i++) {
/*
          outData[op++] = (byte)((inData[p1++] + inData[p2++] + inData[p3++] + inData[p4++])/4); // blue cells avg
          outData[op++] = (byte)((inData[p1++] + inData[p2++] + inData[p3++] + inData[p4++])/4); // green cells avg
          outData[op++] = (byte)((inData[p1++] + inData[p2++] + inData[p3++] + inData[p4++])/4); // red cells avg
*/
          outData[op++] = (byte)(((int)(inData[p1++] & 0xFF) +
            ((int)inData[p2++] & 0xFF)+ ((int)inData[p3++] & 0xFF) +
            ((int)inData[p4++] & 0xFF))/4); // blue cells avg
          outData[op++] = (byte)(((int)(inData[p1++] & 0xFF) +
            ((int)inData[p2++] & 0xFF)+ ((int)inData[p3++] & 0xFF) +
              ((int)inData[p4++] & 0xFF))/4); // blue cells avg
          outData[op++] = (byte)(((int)(inData[p1++] & 0xFF) +
            ((int)inData[p2++] & 0xFF)+ ((int)inData[p3++] & 0xFF) +
              ((int)inData[p4++] & 0xFF))/4); // blue cells avg
          p1 += 3; p2 += 3; p3+= 3; p4 += 3;
        }

      }
    }

    byte[] validateByteArraySize(Buffer buffer,int newSize) {
        Object objectArray=buffer.getData();
        byte[] typedArray;

        if (objectArray instanceof byte[]) {     // is correct type AND not null
            typedArray=(byte[])objectArray;
            if (typedArray.length >= newSize ) { // is sufficient capacity
                return typedArray;
            }

            byte[] tempArray=new byte[newSize];  // re-alloc array
            System.arraycopy(typedArray,0,tempArray,0,typedArray.length);
            typedArray = tempArray;
        } else {
            typedArray = new byte[newSize];
        }

        buffer.setData(typedArray);
        return typedArray;
    }

}
