/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sailpoint.web.tags;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import javax.faces.component.UIComponent;
import javax.faces.context.ResponseWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.chart.urls.PieURLGenerator;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.IntervalCategoryDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.data.statistics.BoxAndWhiskerXYDataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.WindDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.RectangleInsets;

import sailpoint.api.SailPointFactory;
import sailpoint.charting.urls.JavascriptCategoryURLGenerator;
import sailpoint.charting.urls.JavascriptPieURLGenerator;
import sailpoint.object.Configuration;
import sailpoint.tools.FontHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * @author Cagatay Civici
 */
public class ChartUtils {
    
    private static Log log = LogFactory.getLog(ChartUtils.class);
	
	private static String passthruImgAttributes[] = {
		"alt",
		"styleClass",
		"onclick",
		"ondblclick",
		"onmousedown",
		"onmouseup",
		"onmouseover",
		"onmousemove",
		"onmouseout",
		"onkeypress",
		"onkeydown",
		"onkeyup",
		"usemap",
    };
	
	private static Configuration systemConfig;

    /**
     * Loads the current system configuration.
     */
    private static void loadSystemConfiguration() {
        if (systemConfig == null) {
            try {
                systemConfig = SailPointFactory.getCurrentContext().getConfiguration();
            } 
            catch (GeneralException e) {/* do nothing */}
        }
    }
	
	public static void renderPassThruImgAttributes(ResponseWriter writer, UIComponent component) throws IOException{
		for(int i = 0 ; i < passthruImgAttributes.length ; i++) {
			Object value = component.getAttributes().get(passthruImgAttributes[i]);
			if(value != null) {
				writer.writeAttribute(passthruImgAttributes[i], value, null);
			}
		}
		//title attribute overlaps with the chart title so renamed to imgTitle to define img tag's title  
		if(component.getAttributes().get("imgTitle") != null) 
			writer.writeAttribute("title", component.getAttributes().get("imgTitle"), null);
	}

	public static PlotOrientation getPlotOrientation(String orientation) {
		if (orientation.equalsIgnoreCase("horizontal")) {
			return PlotOrientation.HORIZONTAL;
		} else {
			return PlotOrientation.VERTICAL;
		}
	}
	
	public static Color getColor(String color) {
		// HTML colors (#FFFFFF format)
		if (color.startsWith("#")) {
			return new Color(Integer.parseInt(color.substring(1), 16));
		} else {
			// Colors by name
			if (color.equalsIgnoreCase("black"))
				return Color.black;
			if (color.equalsIgnoreCase("grey"))
				return Color.gray;
			if (color.equalsIgnoreCase("yellow"))
				return Color.yellow;
			if (color.equalsIgnoreCase("green"))
				return Color.green;
			if (color.equalsIgnoreCase("blue"))
				return Color.blue;
			if (color.equalsIgnoreCase("red"))
				return Color.red;
			if (color.equalsIgnoreCase("orange"))
				return Color.orange;
			if (color.equalsIgnoreCase("cyan"))
				return Color.cyan;
			if (color.equalsIgnoreCase("magenta"))
				return Color.magenta;
			if (color.equalsIgnoreCase("darkgray"))
				return Color.darkGray;
			if (color.equalsIgnoreCase("lightgray"))
				return Color.lightGray;
			if (color.equalsIgnoreCase("pink"))
				return Color.pink;
			if (color.equalsIgnoreCase("white"))
				return Color.white;
			
			throw new RuntimeException("Unsupported chart color:" + color);
		}
	}
	
	//	Creates the chart with the given chart data
	public static JFreeChart createChartWithType(ChartData chartData, Locale locale) {
		JFreeChart chart = null;
		Object datasource = chartData.getDatasource();
		loadSystemConfiguration();
		Font labelFont = null;
		// If font is configured, use it.
		if(systemConfig.getString(Configuration.CHART_LABEL_FONT_NAME) != null) {
            labelFont = FontHelper.getChartLabelFont(systemConfig);
        }
		if (datasource instanceof PieDataset) {
			chart = createChartWithPieDataSet(chartData, labelFont);
		} else if (datasource instanceof CategoryDataset) {
			chart = createChartWithCategoryDataSet(chartData);
		} else if (datasource instanceof XYDataset) {
			chart = createChartWithXYDataSet(chartData);
		} else if(datasource!=null){
			throw new RuntimeException("Unsupported chart type");
		}
        
		if(chart!=null) {
			Plot plot = chart.getPlot();
            Message msg = new Message(MessageKeys.NO_DATA_FOR_CHART);
            plot.setNoDataMessage(msg.getLocalizedMessage(locale, null));
            plot.setOutlineVisible(false);

            // If chart fonts are defined in init.xml, use them for the charts.
            if(systemConfig.getString(Configuration.CHART_TITLE_FONT_NAME) != null){
                Font titleFont = FontHelper.getChartTitleFont(systemConfig);
                chart.getTitle().setFont(titleFont);
                chart.getLegend().setItemFont(titleFont);
            }
            //Set no data message
            if(systemConfig.getString(Configuration.CHART_BODY_FONT_NAME) != null) {
                Font bodyFont = FontHelper.getChartBodyFont(systemConfig);
                plot.setNoDataMessageFont(bodyFont);
            }
		}

		return chart;
	}
	
	public static void setGeneralChartProperties(JFreeChart chart, ChartData chartData) {
		chart.setBackgroundPaint(ChartUtils.getColor(chartData.getBackground()));
		chart.getPlot().setBackgroundPaint(ChartUtils.getColor(chartData.getForeground()));
		chart.setTitle(chartData.getTitle());
		chart.setAntiAlias(chartData.isAntialias());

		// Alpha transparency (100% means opaque)
		if (chartData.getAlpha() < 100) {
			chart.getPlot().setForegroundAlpha((float) chartData.getAlpha() / 100);
		}
	}

	public static JFreeChart createChartWithCategoryDataSet(ChartData chartData) {
		JFreeChart chart = null;
		PlotOrientation plotOrientation = ChartUtils.getPlotOrientation(chartData.getOrientation());
		try {
		CategoryDataset dataset = (CategoryDataset) chartData.getDatasource();
		String type = chartData.getType();
		String xAxis = chartData.getXlabel();
		String yAxis = chartData.getYlabel();
		boolean is3d = chartData.isChart3d();
		boolean legend = chartData.isLegend();

		if (type.equalsIgnoreCase("bar")) {
			if (is3d == true) {
				chart = ChartFactory.createBarChart3D("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
			} else {
				chart = ChartFactory.createBarChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
			}
			setBarOutline(chart, chartData);
		} else if (type.equalsIgnoreCase("stackedbar")) {
			if (is3d == true) {
				chart = ChartFactory.createStackedBarChart3D("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
			} else {
				chart = ChartFactory.createStackedBarChart("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
			}
			setBarOutline(chart, chartData);
		} else if (type.equalsIgnoreCase("line")) {
			if (is3d == true)
				chart = ChartFactory.createLineChart3D("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
			else
				chart = ChartFactory.createLineChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
            setLineOptions(chart, chartData);
        } else if (type.equalsIgnoreCase("area")) {
			chart = ChartFactory.createAreaChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("stackedarea")) {
			chart = ChartFactory.createStackedAreaChart("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("waterfall")) {
			chart = ChartFactory.createWaterfallChart("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("gantt")) {
			chart = ChartFactory.createGanttChart("", xAxis, yAxis,(IntervalCategoryDataset) dataset, legend, true, false);
		}
        if(chart==null)
            return null;
        if(chartData.isLabels())
            setCategoryItemLabels(chart, chartData);
        
        if(chartData.getUselinks()!=null && !chartData.getUselinks().equals(""))
            setCategoryItemURLs(chart, chartData, new JavascriptCategoryURLGenerator(chartData.getUselinks(), chartData.getTitle()));
        
		setCategorySeriesColors(chart, chartData);
        setCategoryOptions(chart, chartData);
        setCategoryRange(chart, chartData);
        } catch (Exception e) {
            log.error("Exception during chart creation: " + e.getMessage());
        }
		return chart;
	}

	public static JFreeChart createChartWithPieDataSet(ChartData chartData, Font labelFont) {
		PieDataset dataset = (PieDataset) chartData.getDatasource();
		String type = chartData.getType();
		boolean legend = chartData.isLegend();
		JFreeChart chart = null;

		if (type.equalsIgnoreCase("pie")) {
			if (chartData.isChart3d()) {
				chart = ChartFactory.createPieChart3D("", dataset, legend,true, false);
				PiePlot3D plot = (PiePlot3D) chart.getPlot();
				plot.setDepthFactor((float) chartData.getDepth() / 100);
			} else {
				chart = ChartFactory.createPieChart("", dataset, legend, true,false);
			}
		} else if (type.equalsIgnoreCase("ring")) {
			chart = ChartFactory.createRingChart("", dataset, legend, true,false);
		}
        if(chart==null)
            return null;
		PiePlot plot = (PiePlot) chart.getPlot();
		
		plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} = {1}",
		         new DecimalFormat("#,##0"),
		         new DecimalFormat("0"))); 
		plot.setMaximumLabelWidth(0.20);
		plot.setStartAngle((float) chartData.getStartAngle());
		plot.setLabelGap(0.02);
		
		
		setPieSectionColors(chart, chartData);
		if(labelFont != null) {
		    plot.setLabelFont(labelFont);
		}
		else {
		    plot.setLabelFont(new Font(FontHelper.SANS_SERIF_NAME, Font.PLAIN, 9));
		}

        
        if(chartData.getUselinks()!=null)
            setPieItemURLs(chart, chartData, new JavascriptPieURLGenerator(chartData.getUselinks()));
		
		return chart;
	}

	public static JFreeChart createChartWithXYDataSet(ChartData chartData) {
		XYDataset dataset = (XYDataset) chartData.getDatasource();
		String type = chartData.getType();
		String xAxis = chartData.getXlabel();
		String yAxis = chartData.getYlabel();
		boolean legend = chartData.isLegend();

		JFreeChart chart = null;
		PlotOrientation plotOrientation = ChartUtils.getPlotOrientation(chartData.getOrientation());

		if (type.equalsIgnoreCase("timeseries")) {
			chart = ChartFactory.createTimeSeriesChart("", xAxis, yAxis,dataset, legend, true, false);
            setTimeSeriesOptions(chart, chartData);
            
		} else if (type.equalsIgnoreCase("xyline")) {
			chart = ChartFactory.createXYLineChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("polar")) {
			chart = ChartFactory.createPolarChart("", dataset, legend, true,false);
		} else if (type.equalsIgnoreCase("scatter")) {
			chart = ChartFactory.createScatterPlot("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("xyarea")) {
			chart = ChartFactory.createXYAreaChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("xysteparea")) {
			chart = ChartFactory.createXYStepAreaChart("", xAxis, yAxis,dataset, plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("xystep")) {
			chart = ChartFactory.createXYStepChart("", xAxis, yAxis, dataset,plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("bubble")) {
			chart = ChartFactory.createBubbleChart("", xAxis, yAxis,(XYZDataset) dataset, plotOrientation, legend, true, false);
		} else if (type.equalsIgnoreCase("candlestick")) {
			chart = ChartFactory.createCandlestickChart("", xAxis, yAxis,(OHLCDataset) dataset, legend);
		} else if (type.equalsIgnoreCase("boxandwhisker")) {
			chart = ChartFactory.createBoxAndWhiskerChart("", xAxis, yAxis,(BoxAndWhiskerXYDataset) dataset, legend);
		} else if (type.equalsIgnoreCase("highlow")) {
			chart = ChartFactory.createHighLowChart("", xAxis, yAxis,(OHLCDataset) dataset, legend);
		} else if (type.equalsIgnoreCase("histogram")) {
			chart = ChartFactory.createHistogram("", xAxis, yAxis,(IntervalXYDataset) dataset, plotOrientation, legend, true,false);
		} else if (type.equalsIgnoreCase("wind")) {
			chart = ChartFactory.createWindPlot("", xAxis, yAxis,(WindDataset) dataset, legend, true, false);
		}
        if(chart==null)
            return null;
        if(chartData.getRange() > 0)
            setXYRange(chart, chartData);
        if(chartData.isLabels())
            setXYItemLabels(chart, chartData);
		setXYSeriesColors(chart, chartData);
		return chart;
	}
	
	/**
	 * Series coloring
	 * Plot has no getRenderer so two methods for each plot type(categoryplot and xyplot)
	 */
	public static void setCategorySeriesColors(JFreeChart chart, ChartData chartData) {
		if(chart.getPlot() instanceof CategoryPlot) {
			CategoryPlot plot = (CategoryPlot) chart.getPlot();
			if (chartData.getColors() != null && !chartData.getColors().equals("")) {
				String[] colors = chartData.getColors().split(",");
				for (int i = 0; i < colors.length; i++) {
					plot.getRenderer().setSeriesPaint(i, ChartUtils.getColor(colors[i].trim()));
				}
			}
		}
	}
	
	public static void setXYSeriesColors(JFreeChart chart, ChartData chartData) {
		if(chart.getPlot() instanceof XYPlot && chartData.getColors() != null) {
				XYPlot plot = (XYPlot) chart.getPlot();
				String[] colors = chartData.getColors().split(",");
				for (int i = 0; i < colors.length; i++) {
				plot.getRenderer().setSeriesPaint(i, ChartUtils.getColor(colors[i].trim()));
			}
		}
	}
	
	public static void setPieSectionColors(JFreeChart chart, ChartData chartData) {
		if(chart.getPlot() instanceof PiePlot && chartData.getColors() != null) {
			String[] colors = chartData.getColors().split(",");
			for (int i = 0; i < colors.length; i++) {
				((PiePlot)chart.getPlot()).setSectionPaint(i, ChartUtils.getColor(colors[i].trim()));
			}
		}
	}
	
	/**
	 * Sets the outline of the bars
	 */
	public static void setBarOutline(JFreeChart chart, ChartData chartData) {
		CategoryPlot plot = (CategoryPlot) chart.getPlot();			
		BarRenderer barrenderer = (BarRenderer) plot.getRenderer();
		barrenderer.setDrawBarOutline(chartData.isOutline());
     }
    
    public static void setCategoryOptions(JFreeChart chart, ChartData chartData) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        CategoryAxis domainAxis = plot.getDomainAxis();
        if(chartData.getOrientation()!=null && chartData.getOrientation().equals("vertical")){
            domainAxis.setCategoryLabelPositions(
                CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 6.0)
            );
        }
    }
    
    public static void setLineOptions(JFreeChart chart, ChartData chartData) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.white);
        plot.setRangeGridlinePaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.lightGray);
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setShapesFilled(true);
        renderer.setShapesVisible(true);
    }
    
    public static void setTimeSeriesOptions(JFreeChart chart, ChartData chartData) {
        XYPlot plot = (XYPlot) chart.getPlot();
        chart.setBackgroundPaint(Color.white);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.gray);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        
        XYItemRenderer renderer = plot.getRenderer();
        if (renderer instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer rr = (XYLineAndShapeRenderer) renderer;
            rr.setShapesVisible(true);
            rr.setShapesFilled(true);
            rr.setItemLabelsVisible(true);
        }        
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setTickUnit(
            new DateTickUnit(
                DateTickUnit.MONTH, 1, new SimpleDateFormat("MMM-yyyy")
            )
        );
        axis.setVerticalTickLabels(true);
        plot.setDomainAxis(axis);
    }
    
    /**
     * Sets the item urls of the chart
     */
    public static void setCategoryItemURLs(JFreeChart chart, ChartData chartData, CategoryURLGenerator generator) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        CategoryItemRenderer renderer = (CategoryItemRenderer) plot.getRenderer();
        renderer.setItemURLGenerator(generator);
    }
    
    public static void setPieItemURLs(JFreeChart chart, ChartData chartData, PieURLGenerator generator) {
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setURLGenerator(generator);
    }    
    /**
     * Sets the item labels of the chart
     */
    public static void setCategoryItemLabels(JFreeChart chart, ChartData chartData) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        CategoryItemRenderer renderer = (CategoryItemRenderer) plot.getRenderer();
        renderer.setItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setItemLabelFont(new Font("",Font.PLAIN,8));
        renderer.setItemLabelsVisible(true);
        renderer.setToolTipGenerator(new StandardCategoryToolTipGenerator());
    }
    
    public static void setXYItemLabels(JFreeChart chart, ChartData chartData)
    {
        XYPlot plot = (XYPlot) chart.getPlot();
        XYItemRenderer renderer = (XYItemRenderer) plot.getRenderer();
        renderer.setItemLabelGenerator(new StandardXYItemLabelGenerator());
        renderer.setItemLabelFont(new Font("",Font.PLAIN,9));
        renderer.setItemLabelsVisible(true);
        renderer.setToolTipGenerator(new StandardXYToolTipGenerator());
    }
    
    public static void setXYRange(JFreeChart chart, ChartData chartData)
    {
        XYPlot plot = (XYPlot) chart.getPlot();
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setAutoRangeMinimumSize(chartData.getRange());
    }
    
    public static void setCategoryRange(JFreeChart chart, ChartData chartData)
    {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        ValueAxis rangeAxis = plot.getRangeAxis();        
        if(chartData.getRange() > 0) {
            rangeAxis.setAutoRangeMinimumSize(chartData.getRange());
        }
        else {
            double upperBound = rangeAxis.getUpperBound();
            //Add 10% to upperbound to allow text labels to fit correctly
            rangeAxis.setUpperBound(upperBound + (upperBound/10));
        }
    }
}
