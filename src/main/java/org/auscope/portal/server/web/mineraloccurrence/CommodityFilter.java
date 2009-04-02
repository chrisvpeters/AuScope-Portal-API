package org.auscope.portal.server.web.mineraloccurrence;

/**
 * User: Michael Stegherr
 * Date: 23/03/2009
 * Time: 1:59:02 PM
 */
public class CommodityFilter implements IFilter {
    private String commodityGroup;
    private String commodityName;

    public CommodityFilter(String commodityGroup,
                           String commodityName) {
        this.commodityGroup = commodityGroup;
        this.commodityName  = commodityName;
    }

    /**
     * Build the query string based on given properties
     * @return
     */
    public String getFilterString() {
        StringBuffer queryString = new StringBuffer();
        
        queryString.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<wfs:GetFeature version=\"1.1.0\" xmlns:mo=\"urn:cgi:xmlns:GGIC:MineralOccurrence:1.0\"\n" +
                "                xmlns:wfs=\"http://www.opengis.net/wfs\"\n" +
                "                xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "                xsi:schemaLocation=\"http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\" maxFeatures=\"1000\">\n");

        //if there is no name and group specified then just get all of them
        if(commodityName.equals("") && commodityGroup.equals("")) {
            queryString.append("    <wfs:Query typeName=\"mo:Commodity\"/>\n" +
                               "</wfs:GetFeature>");
            return queryString.toString();
        }
        
        //if we have a name, then build a query to find the commodities with that name
        else {
            queryString.append("    <wfs:Query typeName=\"mo:Commodity\">\n" +
                               "<ogc:Filter xmlns:ogc=\"http://www.opengis.net/ogc\">\n");
            
            if(checkMany())
                queryString.append("<ogc:And>");

            if(!this.commodityGroup.equals(""))
                queryString.append("<ogc:PropertyIsEqualTo>\n" +
                        "                   <ogc:PropertyName>mo:commodityGroup</ogc:PropertyName>\n" +
                        "                   <ogc:Literal>"+this.commodityGroup+"</ogc:Literal>\n" +
                        "           </ogc:PropertyIsEqualTo>");

            if(!this.commodityName.equals(""))
                queryString.append("<ogc:PropertyIsEqualTo>\n" +
                        "                   <ogc:PropertyName>mo:commodityName</ogc:PropertyName>\n" +
                        "                   <ogc:Literal>"+this.commodityName+"</ogc:Literal>\n" +
                        "           </ogc:PropertyIsEqualTo>");

            if(checkMany())
                queryString.append("</ogc:And>");

            queryString.append("</ogc:Filter>\n" +
                    "    </wfs:Query>\n" +
                    "</wfs:GetFeature>");

            return queryString.toString();
        }

    }
    

    /**
     * Do more than one query parameter have a value
     * @return
     */
    private boolean checkMany() {
        int howManyHaveaValue = 0;

        if(!this.commodityGroup.equals(""))
            howManyHaveaValue++;
        if(!this.commodityName.equals(""))
            howManyHaveaValue++;

        if(howManyHaveaValue >= 2)
            return true;

        return false;
    }
}
