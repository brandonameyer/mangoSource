/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.vo.report;

import java.awt.Color;
import java.awt.Paint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.TextAnchor;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.io.StreamUtils;
import com.serotonin.mango.db.dao.SystemSettingsDao;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.util.mindprod.StripEntities;
import com.serotonin.util.StringUtils;
import org.jfree.chart.plot.ValueMarker;

/**
 * @author Matthew Lohbihler
 */
public class ImageChartUtils {
    private static final int NUMERIC_DATA_INDEX = 0;
    private static final int DISCRETE_DATA_INDEX = 1;

    // Add a new getChartData function that accepts new properties
    public static byte[] getChartData(PointTimeSeriesCollection pointTimeSeriesCollection, int width, int height, boolean charttype, String title, String xtitle, String ytitle, double yref) {
        // Pass new properties to new getChartData
        return getChartData(pointTimeSeriesCollection, pointTimeSeriesCollection.hasMultiplePoints(), width, height, charttype, title, xtitle, ytitle, yref);
    }

    public static byte[] getChartData(PointTimeSeriesCollection pointTimeSeriesCollection, int width, int height) {
        return getChartData(pointTimeSeriesCollection, pointTimeSeriesCollection.hasMultiplePoints(), width, height);
    }

    // Add new getChartData that accepts new properties
    public static byte[] getChartData(PointTimeSeriesCollection pointTimeSeriesCollection, boolean showLegend,
            int width, int height, boolean charttype, String title, String xtitle, String ytitle, double yref) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Send new properties to new wrietChartFull
            writeChartFull(pointTimeSeriesCollection, showLegend, out, width, height, charttype, title, xtitle, ytitle, yref);
            return out.toByteArray();
        }
        catch (IOException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    public static byte[] getChartData(PointTimeSeriesCollection pointTimeSeriesCollection, boolean showLegend,
            int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeChart(pointTimeSeriesCollection, showLegend, out, width, height);
            return out.toByteArray();
        }
        catch (IOException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    // New writeChartFull that accepts all new properties
    public static void writeChartFull(PointTimeSeriesCollection pointTimeSeriesCollection, boolean showLegend,
            OutputStream out, int width, int height, boolean charttype, String title, String xtitle, String ytitle, double yref) throws IOException {

        XYLineAndShapeRenderer numericRenderer;
        // Set title, x title, y title for chart
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title, xtitle, ytitle, null, showLegend, false, false);
        
        chart.setBackgroundPaint(SystemSettingsDao.getColour(SystemSettingsDao.CHART_BACKGROUND_COLOUR));
        // Create the y reference line
        ValueMarker marker = new ValueMarker(yref);
        marker.setPaint(Color.RED);

        XYPlot plot = chart.getXYPlot();
        plot.addRangeMarker(marker);
        plot.setBackgroundPaint(SystemSettingsDao.getColour(SystemSettingsDao.PLOT_BACKGROUND_COLOUR));
        Color gridlines = SystemSettingsDao.getColour(SystemSettingsDao.PLOT_GRIDLINE_COLOUR);
        plot.setDomainGridlinePaint(gridlines);
        plot.setRangeGridlinePaint(gridlines);

        double numericMin = 0;
        double numericMax = 1;
        if (pointTimeSeriesCollection.hasNumericData()) {
            //            XYSplineRenderer numericRenderer = new XYSplineRenderer();
            //            numericRenderer.setBaseShapesVisible(false);
            // If the chart type true (line) make a line chart
            if (charttype == true) {
                numericRenderer = new XYLineAndShapeRenderer(true, false);
            }
            // If scatter make scatter plot (shapes true line false)
            else {
                numericRenderer = new XYLineAndShapeRenderer(false, true);
            }

            plot.setDataset(NUMERIC_DATA_INDEX, pointTimeSeriesCollection.getNumericTimeSeriesCollection());
            plot.setRenderer(NUMERIC_DATA_INDEX, numericRenderer);

            for (int i = 0; i < pointTimeSeriesCollection.getNumericPaint().size(); i++) {
                Paint paint = pointTimeSeriesCollection.getNumericPaint().get(i);
                if (paint != null)
                    numericRenderer.setSeriesPaint(i, paint, false);
            }

            numericMin = plot.getRangeAxis().getLowerBound();
            numericMax = plot.getRangeAxis().getUpperBound();

            if (!pointTimeSeriesCollection.hasMultiplePoints()) {
                // If this chart displays a single point, check if there should be a range description.
                TimeSeries timeSeries = pointTimeSeriesCollection.getNumericTimeSeriesCollection().getSeries(0);
                String desc = timeSeries.getRangeDescription();
                if (!StringUtils.isEmpty(desc)) {
                    // Replace any HTML entities with Java equivalents
                    desc = StripEntities.stripHTMLEntities(desc, ' ');
                    plot.getRangeAxis().setLabel(desc);
                }
            }
        }
        else
            plot.getRangeAxis().setVisible(false);

        if (pointTimeSeriesCollection.hasDiscreteData()) {
            XYStepRenderer discreteRenderer = new XYStepRenderer();
            plot.setRenderer(DISCRETE_DATA_INDEX, discreteRenderer, false);

            // Plot the data
            int discreteValueCount = pointTimeSeriesCollection.getDiscreteValueCount();
            double interval = (numericMax - numericMin) / (discreteValueCount + 1);
            TimeSeriesCollection timeSeriesCollection = new TimeSeriesCollection();

            int intervalIndex = 1;
            for (int i = 0; i < pointTimeSeriesCollection.getDiscreteSeriesCount(); i++) {
                DiscreteTimeSeries dts = pointTimeSeriesCollection.getDiscreteTimeSeries(i);
                TimeSeries ts = new TimeSeries(dts.getName(), null, null, Second.class);

                for (PointValueTime pvt : dts.getValueTimes())
                    ImageChartUtils.addSecond(ts, pvt.getTime(),
                            numericMin + (interval * (dts.getValueIndex(pvt.getValue()) + intervalIndex)));

                timeSeriesCollection.addSeries(ts);

                intervalIndex += dts.getDiscreteValueCount();
            }

            plot.setDataset(DISCRETE_DATA_INDEX, timeSeriesCollection);

            // Add the value annotations.
            double annoX = plot.getDomainAxis().getLowerBound();
            intervalIndex = 1;
            for (int i = 0; i < pointTimeSeriesCollection.getDiscreteSeriesCount(); i++) {
                DiscreteTimeSeries dts = pointTimeSeriesCollection.getDiscreteTimeSeries(i);
                if (dts.getPaint() != null)
                    discreteRenderer.setSeriesPaint(i, dts.getPaint());

                for (int j = 0; j < dts.getDiscreteValueCount(); j++) {
                    XYTextAnnotation anno = new XYTextAnnotation(" " + dts.getValueText(j), annoX, numericMin
                            + (interval * (j + intervalIndex)));
                    if (!pointTimeSeriesCollection.hasNumericData() && intervalIndex + j == discreteValueCount)
                        // This prevents the top label from getting cut off
                        anno.setTextAnchor(TextAnchor.TOP_LEFT);
                    else
                        anno.setTextAnchor(TextAnchor.BOTTOM_LEFT);
                    anno.setPaint(discreteRenderer.lookupSeriesPaint(i));
                    plot.addAnnotation(anno);
                }

                intervalIndex += dts.getDiscreteValueCount();
            }
        }

        // Return the image.
        ChartUtilities.writeChartAsPNG(out, chart, width, height);
    }

    // Retain old writeChart for consolidated chart support
    public static void writeChart(PointTimeSeriesCollection pointTimeSeriesCollection, boolean showLegend,
            OutputStream out, int width, int height) throws IOException {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, null, showLegend, false, false);
        chart.setBackgroundPaint(SystemSettingsDao.getColour(SystemSettingsDao.CHART_BACKGROUND_COLOUR));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(SystemSettingsDao.getColour(SystemSettingsDao.PLOT_BACKGROUND_COLOUR));
        Color gridlines = SystemSettingsDao.getColour(SystemSettingsDao.PLOT_GRIDLINE_COLOUR);
        plot.setDomainGridlinePaint(gridlines);
        plot.setRangeGridlinePaint(gridlines);

        double numericMin = 0;
        double numericMax = 1;
        if (pointTimeSeriesCollection.hasNumericData()) {
            //            XYSplineRenderer numericRenderer = new XYSplineRenderer();
            //            numericRenderer.setBaseShapesVisible(false);
            XYLineAndShapeRenderer numericRenderer = new XYLineAndShapeRenderer(true, false);

            plot.setDataset(NUMERIC_DATA_INDEX, pointTimeSeriesCollection.getNumericTimeSeriesCollection());
            plot.setRenderer(NUMERIC_DATA_INDEX, numericRenderer);

            for (int i = 0; i < pointTimeSeriesCollection.getNumericPaint().size(); i++) {
                Paint paint = pointTimeSeriesCollection.getNumericPaint().get(i);
                if (paint != null)
                    numericRenderer.setSeriesPaint(i, paint, false);
            }

            numericMin = plot.getRangeAxis().getLowerBound();
            numericMax = plot.getRangeAxis().getUpperBound();

            if (!pointTimeSeriesCollection.hasMultiplePoints()) {
                // If this chart displays a single point, check if there should be a range description.
                TimeSeries timeSeries = pointTimeSeriesCollection.getNumericTimeSeriesCollection().getSeries(0);
                String desc = timeSeries.getRangeDescription();
                if (!StringUtils.isEmpty(desc)) {
                    // Replace any HTML entities with Java equivalents
                    desc = StripEntities.stripHTMLEntities(desc, ' ');
                    plot.getRangeAxis().setLabel(desc);
                }
            }
        }
        else
            plot.getRangeAxis().setVisible(false);

        if (pointTimeSeriesCollection.hasDiscreteData()) {
            XYStepRenderer discreteRenderer = new XYStepRenderer();
            plot.setRenderer(DISCRETE_DATA_INDEX, discreteRenderer, false);

            // Plot the data
            int discreteValueCount = pointTimeSeriesCollection.getDiscreteValueCount();
            double interval = (numericMax - numericMin) / (discreteValueCount + 1);
            TimeSeriesCollection timeSeriesCollection = new TimeSeriesCollection();

            int intervalIndex = 1;
            for (int i = 0; i < pointTimeSeriesCollection.getDiscreteSeriesCount(); i++) {
                DiscreteTimeSeries dts = pointTimeSeriesCollection.getDiscreteTimeSeries(i);
                TimeSeries ts = new TimeSeries(dts.getName(), null, null, Second.class);

                for (PointValueTime pvt : dts.getValueTimes())
                    ImageChartUtils.addSecond(ts, pvt.getTime(),
                            numericMin + (interval * (dts.getValueIndex(pvt.getValue()) + intervalIndex)));

                timeSeriesCollection.addSeries(ts);

                intervalIndex += dts.getDiscreteValueCount();
            }

            plot.setDataset(DISCRETE_DATA_INDEX, timeSeriesCollection);

            // Add the value annotations.
            double annoX = plot.getDomainAxis().getLowerBound();
            intervalIndex = 1;
            for (int i = 0; i < pointTimeSeriesCollection.getDiscreteSeriesCount(); i++) {
                DiscreteTimeSeries dts = pointTimeSeriesCollection.getDiscreteTimeSeries(i);
                if (dts.getPaint() != null)
                    discreteRenderer.setSeriesPaint(i, dts.getPaint());

                for (int j = 0; j < dts.getDiscreteValueCount(); j++) {
                    XYTextAnnotation anno = new XYTextAnnotation(" " + dts.getValueText(j), annoX, numericMin
                            + (interval * (j + intervalIndex)));
                    if (!pointTimeSeriesCollection.hasNumericData() && intervalIndex + j == discreteValueCount)
                        // This prevents the top label from getting cut off
                        anno.setTextAnchor(TextAnchor.TOP_LEFT);
                    else
                        anno.setTextAnchor(TextAnchor.BOTTOM_LEFT);
                    anno.setPaint(discreteRenderer.lookupSeriesPaint(i));
                    plot.addAnnotation(anno);
                }

                intervalIndex += dts.getDiscreteValueCount();
            }
        }

        // Return the image.
        ChartUtilities.writeChartAsPNG(out, chart, width, height);
    }

    // public static void writeChart(TimeSeries timeSeries, OutputStream out, int width, int height) throws IOException
    // {
    // writeChart(new TimeSeriesCollection(timeSeries), false, out, width, height);
    // }
    //    
    // public static void writeChart(TimeSeriesCollection timeSeriesCollection, boolean showLegend, OutputStream out,
    // int width, int height) throws IOException {
    // JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, timeSeriesCollection, showLegend,
    // false, false);
    // chart.setBackgroundPaint(Color.white);
    //        
    // // Change the plot renderer
    // // XYPlot plot = chart.getXYPlot();
    // // XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    // // plot.setRenderer(renderer);
    //        
    // // Return the image.
    // ChartUtilities.writeChartAsPNG(out, chart, width, height);
    // }

    public static void writeChart(HttpServletResponse response, byte[] chartData) throws IOException {
        response.setContentType(getContentType());
        StreamUtils.transfer(new ByteArrayInputStream(chartData), response.getOutputStream());
    }

    public static String getContentType() {
        return "image/x-png";
    }

    public static void addSecond(TimeSeries timeSeries, long time, Number value) {
        try {
            timeSeries.add(new Second(new Date(time)), value);
        }
        catch (SeriesException e) { /* duplicate Second. Ignore. */
        }
    }
}
