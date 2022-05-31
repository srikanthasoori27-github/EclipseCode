/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import net.sf.jasperreports.engine.JRChart;
import net.sf.jasperreports.engine.JRChartCustomizer;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.renderer.category.BarRenderer;

import java.awt.*;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class DefaultChartCustomizer implements JRChartCustomizer {

    public void customize(JFreeChart chart, JRChart jasperChart) {

        // Increase the chart title size
        if (chart.getTitle() != null && chart.getTitle().getFont() != null){
            Font font = chart.getTitle().getFont();
            chart.getTitle().setFont(font.deriveFont(Font.BOLD, 14));
        }

        Plot plot = chart.getPlot();
        if (plot instanceof PiePlot) {
          PiePlot piePlot = (PiePlot)plot;
          piePlot.setLabelGenerator(null);
        }

        if (plot instanceof CategoryPlot) {
			CategoryPlot categoryPlot = (CategoryPlot)plot;
			ValueAxis valueAxis = categoryPlot.getRangeAxis();
            CategoryAxis categoryAxis = categoryPlot.getDomainAxis();

			// The default upper margin is 5%.
			// This is nearly always no good if labels are displayed.
			// We need to calculate the height needed for the top label
			// and then set the upper margin appropriately.
			// Instead of doing that, I hard-code the margin to 40% for this test.
			valueAxis.setUpperMargin(0.40);

            // Use integer tick marks
            valueAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

            // I don't know what the default MaximumCategoryLabelWidthRatio is,
			// but it's too small in many cases.
			categoryAxis.setMaximumCategoryLabelWidthRatio(0.8f);

			// The ItemMargin is the space between bars within a single category.
			// The default value is 10% (0.10).
			// It's common to want this smaller.
			if (categoryPlot.getRenderer() instanceof BarRenderer) {
				BarRenderer barRenderer = (BarRenderer)categoryPlot.getRenderer();
				barRenderer.setItemMargin(0.0);
			}

			// By default category labels are a single line.
			categoryAxis.setMaximumCategoryLabelLines(2);
		}

    }
}
