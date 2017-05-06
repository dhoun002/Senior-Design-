package org.openpnp.vision.pipeline.stages;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.RotatedRect;
import org.opencv.core.Point;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;

import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.stages.convert.ColorConverter;
import org.openpnp.vision.pipeline.Stage;
import org.openpnp.vision.pipeline.Property;
import org.openpnp.vision.FluentCv;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.convert.Convert;
import org.pmw.tinylog.Logger;

@Stage(
  category="Image Processing", 
  description="Mask an image with multiple shapes originating from previous stages or formed with numeric input provided by the user.")
  
public class MaskMultiple extends CvStage {

    @Element(required = false)
    @Convert(ColorConverter.class)
    @Property(description="Color of mask.")
    private Color color = Color.black;

    @Attribute(required = false)
    @Property(description="Stage(s) to input shapes from, or coordinates forming shapes: One (1) coordinate pair + radius for circles, 4 coordinate pairs or more, for polygons. Multiple stages or shapes should be separated by semicolons ';'")
    private String shapes = null;
    
    @Attribute(required = false)
    @Property(description="Invert the mask.")
    private boolean inverted = false;
    
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    
    public String getShapes() {
        return shapes;
    }

    public void setShapes(String shapes) {
        this.shapes = shapes;
    }

    public boolean isInverted() {
        return inverted;
    }
    
    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    @Override
    public Result process(CvPipeline pipeline) throws Exception {
        if (shapes == null) {
            throw new Exception("Mask shapes must be specified.");
        }
        /*
        decide about the format of the input
         - string without ',' = stage name
         - 3 comma separated numbers = circle center coords + radius 
         - 6 or more (multiple of 2) comma separated numbers = polygon
         - tuples of comma separated numbers, separated by semicolons = multiple shapes
        */
        Mat mat = pipeline.getWorkingImage();
        Mat mask = mat.clone();
        mask.setTo(FluentCv.colorToScalar(color == null ? FluentCv.indexedColor(0) : color));
        Mat masked = mask.clone();
        
        String[] items = shapes.split(";");
        // we will be constructing an array of polygons
        ArrayList<MatOfPoint> poly = new ArrayList<MatOfPoint>();
        
        for (String item : items) {
        
          String[] atoms = item.split(","); 
          switch (atoms.length) {
            case 1:
              // this is assumed to be a stage name
              Result result = pipeline.getResult(item);
              if (result == null) {
                // be nice to the user
                continue;
              }
              if (result.model instanceof RotatedRect) {
                // just one RotatedRect
                RotatedRect rect = (RotatedRect) result.model;
                // we need this to extract the points
                Point[] points = new Point[4];
                // get the 4 points
                rect.points(points);
                // reuse poly
                poly.clear();
                // convert rect for fillPoly
                poly.add(new MatOfPoint(points));
                // draw rect as poly
                Core.fillPoly(mask,poly,new Scalar(255,255,255));
                
              } else if (result.model instanceof Result.Circle) {
                // just one circle
                Result.Circle circle = (Result.Circle) result.model;
                Core.circle(mask, new Point(circle.x, circle.y), (int) circle.diameter/2, new Scalar(255,255,255), -1);                
                
              } else if (result.model instanceof List<?>) {
                // we've got multiple Circles or RotatedRects
                ArrayList multi = (ArrayList) result.model;
                if (multi.get(0) instanceof  Result.Circle) {
                  // a collection of circles 
                  for (int i=0; i < multi.size(); i++) {
                    Result.Circle circle = (Result.Circle) multi.get(i);
                    Core.circle(mask, new Point(circle.x, circle.y), (int) circle.diameter/2, new Scalar(255,255,255), -1);
                  }
                } else if (multi.get(0) instanceof RotatedRect) {
                  // a collection of Rotatedmulti
                  Point[] points = new Point[4];
                  for (int i=0; i < multi.size(); i++) {
                    // get the 4 points of each rotated rect
                    RotatedRect rect = (RotatedRect) multi.get(i);
                    rect.points(points);
                    // reuse poly
                    poly.clear();
                    // convert rect for fillPoly
                    poly.add(new MatOfPoint(points));
                    Core.fillPoly(mask,poly,new Scalar(255,255,255));
                  }
                }
              }
              break;
            case 3:
              // this is a circle
              Core.circle(mask, new Point(Integer.parseInt(atoms[0]),Integer.parseInt(atoms[1])), Integer.parseInt(atoms[2]), new Scalar(255,255,255), -1);
              break;
            default:
              if (atoms.length >= 6 && atoms.length % 2 == 0) {
                // this is a polygon
                Point[] points = new Point[(int)atoms.length/2];
                poly.clear();
                for (int i=0; i< atoms.length/2; i++) {
                  points[i] = new Point(Integer.parseInt(atoms[i*2]),Integer.parseInt(atoms[i*2+1]));
                }
                poly.add(new MatOfPoint(points));
                Core.fillPoly(mask,poly,new Scalar(255,255,255));
                
              } else {
                // this is an error
                Logger.error("Wrong number of coords. Should be a comma separated even series of numbers >= 6, but got {}",item);
              }
              break;
          }
        }
        if (inverted) {
          Core.bitwise_not(mask, mask);
        }
        mat.copyTo(masked, mask);
        mask.release();
        return new Result(masked,null);
    }
}
