/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import net.sf.jasperreports.charts.JRCategorySeries;
import net.sf.jasperreports.charts.design.JRDesignCategoryDataset;
import net.sf.jasperreports.charts.design.JRDesignCategorySeries;
import net.sf.jasperreports.charts.design.JRDesignPieDataset;
import net.sf.jasperreports.charts.design.JRDesignPieSeries;
import net.sf.jasperreports.engine.JRChart;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignChart;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignDatasetRun;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.EvaluationTimeEnum;
import sailpoint.object.Chart;

import java.util.List;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class JasperChartBuilder {

    private JasperDesign design;
    private Chart chart;
    private List<String> seriesList;

    public JasperChartBuilder(Chart chart, List<String> seriesList, JasperDesign design) {
        this.chart = chart;
        this.design = design;
        this.seriesList = seriesList;
    }

    public JRDesignDataset buildChartDS() throws JRException {
        JRDesignDataset chartDS = new JRDesignDataset(false);
        chartDS.setName("ChartDS");

        JRDesignField catField = new JRDesignField();
        catField.setName(Chart.FIELD_CATEGORY);
        chartDS.addField(catField);

        JRDesignField valueField = new JRDesignField();
        valueField.setName(Chart.FIELD_VALUE);
        valueField.setValueClass(Number.class);
        chartDS.addField(valueField);

        JRDesignField seriesField = new JRDesignField();
        seriesField.setName(Chart.FIELD_SERIES);
        chartDS.addField(seriesField);

        return chartDS;
    }

    public JRDesignChart buildChart(){

        JRDesignDatasetRun chartDsRun = new JRDesignDatasetRun();
        chartDsRun.setDataSourceExpression(new JRDesignExpression("$P{chartDS}"));
        chartDsRun.setDatasetName("ChartDS");

        JRDesignChart jrChart = null;
        if (Chart.CHART_TYPE_PIE.equals(chart.getType())){
            jrChart = buildPieChart(chartDsRun);
        } else if (Chart.CHART_TYPE_COLUMN.equals(chart.getType())){
            jrChart = buildColumnChart(chartDsRun);
        } else if (Chart.CHART_TYPE_LINE.equals(chart.getType())){
            jrChart = buildLineChart(chartDsRun);
        } else {
            throw new RuntimeException("Unknown chart type '"+chart.getType()+"'");
        }

        if (chart.getCustomizerClass() == null){
            jrChart.setCustomizerClass(DefaultChartCustomizer.class.getName());
        } else {
            jrChart.setCustomizerClass(chart.getCustomizerClass());
        }

        if (chart.getTitle() != null){
            String titleStr = "\""+chart.getTitle()+"\"";
            jrChart.setTitleExpression(new JRDesignExpression(titleStr));
        }

        return jrChart;
    }

    private JRDesignChart buildPieChart(JRDesignDatasetRun chartDsRun){

        JRDesignPieDataset pieDS = new JRDesignPieDataset(null);
        pieDS.setDatasetRun(chartDsRun);

        JRDesignPieSeries series = new JRDesignPieSeries();
        series.setKeyExpression(new JRDesignExpression("$F{"+Chart.FIELD_CATEGORY+"}"));
        series.setValueExpression(new JRDesignExpression("$F{"+Chart.FIELD_VALUE+"}"));
        pieDS.addPieSeries(series);

        JRDesignChart chart = new JRDesignChart(design, JRChart.CHART_TYPE_PIE);
        chart.setX(315);
        chart.setY(30);
        chart.setWidth(400);
        chart.setHeight(250);
        chart.setDataset(pieDS);
        chart.setShowLegend(true);
        chart.setEvaluationTime(EvaluationTimeEnum.REPORT);

        return chart;
    }

    private JRDesignChart buildColumnChart(JRDesignDatasetRun chartDsRun){

        JRDesignCategoryDataset ds = new JRDesignCategoryDataset(null);
        ds.setDatasetRun(chartDsRun);

        if (seriesList != null && !seriesList.isEmpty()){
            for(String series : seriesList){
                JRCategorySeries jrSeries = initColumnSeries(series);
                ds.addCategorySeries(jrSeries);
            }
        }

        JRDesignChart jrChart = new JRDesignChart(design, JRChart.CHART_TYPE_BAR);
        jrChart.setX(315);
        jrChart.setY(30);
        jrChart.setWidth(400);
        jrChart.setHeight(250);
        jrChart.setDataset(ds);
        jrChart.setEvaluationTime(EvaluationTimeEnum.REPORT);
        jrChart.setShowLegend(true);

        return jrChart;
    }

    private JRCategorySeries initColumnSeries(String series){

        JRDesignCategorySeries jrSeries = new JRDesignCategorySeries();
        jrSeries.setSeriesExpression(new JRDesignExpression("$F{"+Chart.FIELD_SERIES+"}"));
        jrSeries.setLabelExpression(new JRDesignExpression("$F{"+Chart.FIELD_CATEGORY+"}"));
        jrSeries.setValueExpression(new JRDesignExpression("$F{"+Chart.FIELD_VALUE+"}"));
        jrSeries.setCategoryExpression(new JRDesignExpression("$F{"+Chart.FIELD_CATEGORY+"}"));

        return jrSeries;
    }

    private JRDesignChart buildLineChart(JRDesignDatasetRun chartDsRun){
        JRDesignCategoryDataset ds = new JRDesignCategoryDataset(null);
        ds.setDatasetRun(chartDsRun);

        JRDesignCategorySeries series1 = new JRDesignCategorySeries();
        series1.setCategoryExpression(new JRDesignExpression("$F{"+Chart.FIELD_CATEGORY+"}"));
        series1.setValueExpression(new JRDesignExpression("$F{"+Chart.FIELD_VALUE+"}"));
        series1.setSeriesExpression(new JRDesignExpression("$F{"+Chart.FIELD_SERIES+"}"));

        ds.addCategorySeries(series1);

        JRDesignChart chart = new JRDesignChart(design, JRChart.CHART_TYPE_LINE);
        chart.setX(315);
        chart.setY(30);
        chart.setWidth(400);
        chart.setHeight(250);
        chart.setDataset(ds);
        chart.setShowLegend(true);
        chart.setEvaluationTime(EvaluationTimeEnum.REPORT);

        return chart;
    }

}
