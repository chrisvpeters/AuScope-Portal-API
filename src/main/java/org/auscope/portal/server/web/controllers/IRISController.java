package org.auscope.portal.server.web.controllers;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

import org.auscope.portal.core.server.controllers.BasePortalController;
import org.auscope.portal.core.services.namespaces.IterableNamespace;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

@Controller
public class IRISController extends BasePortalController {
    /**
     * The format with which to encode input stream.
     */
    private static final String ENCODING = "ISO-8859-1";

    /**
     * The XPath object that's used to create expressions to extract information from the IRIS responses.
     */
    private XPath xPath;

    /**
     * Makes sure that a string has a trailing forward slash.
     * @param string The string that you want to have a trailing forward slash.
     * @return The original string, modified if required, with a trailing forward slash.
     */
    private String ensureTrailingForwardslash(String string) {
        // Make sure the trailing separator is in place:
        return string.endsWith("/") ? string : string + "/";
    }

    /**
     * Instantiates the IRISController object.
     *
     * Initialises the xPath object.
     */
    public IRISController() {
        this.xPath = XPathFactory.newInstance().newXPath();
        this.xPath.setNamespaceContext(new IterableNamespace() {
            {
                map.put("default", "http://www.data.scec.org/xml/station/");
            }
        });
    }

    /**
     * Downloads a resource from a URL and returns it as Document object.
     * @param queryUrl The URL of the resource you require.
     * @return The resource at the URL requested, converted to a Document object.
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    private Document getDocumentFromURL(String queryUrl)
            throws IOException, ParserConfigurationException, SAXException {
        String irisResponse = getIrisResponseFromQuery(queryUrl);
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(irisResponse.getBytes(ENCODING)));
    }

    /**
     * Downloads a resource from a URL and returns it as a String object.
     * @param queryUrl The URL of the resource you require.
     * @return The resource at the requested URL, as a String object.
     * @throws IOException
     */
    protected String getIrisResponseFromQuery(String queryUrl) throws IOException {
        // NB: This method is protected so that it can be overridden in order to break external dependencies in tests.
        //TODO VT: As part of the review for AGOS-15 , we should be following the same architecture as the rest of portal and use
        // HttpServiceCaller to make any external http requests.
        InputStream inputStream = new URL(queryUrl).openStream();
        Scanner scanner = new Scanner(inputStream, ENCODING);
        String irisResponse = scanner.useDelimiter("\\A").next();
        scanner.close();
        inputStream.close();
        return irisResponse;
    }

    /**
     * Makes a request to the IRIS service specified, for all the stations
     * on the network code provided.
     *
     * The response is converted to some KML points to be rendered on the
     * map.
     *
     * The request will look something like this: http://www.iris.edu/ws/station/query?net=S
     *
     * @param serviceUrl The IRIS web service URL.
     * @param networkCode The network code that you're interested in.
     * @return a JSONResponseMAV containing KML points of each station.
     */
    @RequestMapping("/getIRISStations.do")
    public ModelAndView getIRISStations(
        @RequestParam("serviceUrl") String serviceUrl,
        @RequestParam("networkCode") String networkCode) {
        serviceUrl = ensureTrailingForwardslash(serviceUrl);

        try {
            Document irisDoc = getDocumentFromURL(serviceUrl + "station/query?net=" + networkCode);

            //TODO VT: As part of the review for AGOS-15 , we should be following the same architecture as the rest of portal and
            // create a xslt file to do the transformation from xml to kml
            NodeList stations = irisDoc.getDocumentElement().getElementsByTagName("Station");
            XPathExpression nameExpression = xPath.compile("default:StationEpoch/default:Site/default:Name/text()");
            XPathExpression latExpression = xPath.compile("default:StationEpoch/default:Lat/text()");
            XPathExpression lonExpression = xPath.compile("default:StationEpoch/default:Lon/text()");

            StringBuilder kml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>GML Links to KML</name><description><![CDATA[GeoSciML data converted to KML]]></description>");

            // For each station:
            for (int i = 0; i < stations.getLength(); i++) {
                Node station = stations.item(i);
                Node staCode = station.getAttributes().getNamedItem("sta_code");
                kml.append(
                        String.format(
                                "<Placemark><name>%s</name><description><![CDATA[GENERIC_PARSER:%s]]></description>" +
                                "<MultiGeometry><Point><coordinates>%s,%s,0</coordinates></Point></MultiGeometry></Placemark>",
                                nameExpression.evaluate(station, XPathConstants.STRING),
                                staCode.getTextContent(),
                                lonExpression.evaluate(station, XPathConstants.STRING),
                                latExpression.evaluate(station, XPathConstants.STRING)));
            }

            kml.append("</Document></kml>");

            return generateJSONResponseMAV(true, "gml", kml.toString(), null);
        } catch (Exception e) {
            return generateJSONResponseMAV(false, e.getMessage(), "Failed.");
        }
    }

    /**
     * Makes a request to the IRIS service for a particular station's channels.
     *
     * The request will look something like this: http://www.iris.edu/ws/station/query?net=S&station=AUDAR&level=chan
     *
     * @param serviceUrl The IRIS web service URL.
     * @param networkCode The network code that you're interested in.
     * @param stationCode The code of the station to interrogate.
     * @return a JSONResponseMAV containing the start and end dates of the site and
     * a collection of channel codes.
     */
    @RequestMapping("/getStationChannels.do")
    public ModelAndView getStationChannels(
        @RequestParam("serviceUrl") String serviceUrl,
        @RequestParam("networkCode") String networkCode,
        @RequestParam("stationCode") String stationCode) {
        serviceUrl = ensureTrailingForwardslash(serviceUrl);
        try {
            Document irisDoc = getDocumentFromURL(serviceUrl + "station/query?net=" + networkCode + "&station=" + stationCode + "&level=chan");

            NodeList channels = irisDoc.getDocumentElement().getElementsByTagName("Channel");

            String[] channelCodes = new String[channels.getLength()];

            // For each station:
            for (int i = 0; i < channels.getLength(); i++) {
                Node channel = channels.item(i);
                channelCodes[i] = channel.getAttributes().getNamedItem("chan_code").getTextContent();
            }

            String startDate = xPath.compile("//default:Station[@sta_code='" + stationCode + "']/default:StationEpoch/default:StartDate").evaluate(irisDoc, XPathConstants.STRING).toString();
            String endDate = xPath.compile("//default:Station[@sta_code='" + stationCode + "']/default:StationEpoch/default:EndDate").evaluate(irisDoc, XPathConstants.STRING).toString();

            ModelMap channelInfo = new ModelMap();
            channelInfo.put("start_date", startDate);
            channelInfo.put("end_date", endDate);
            channelInfo.put("channel_codes", channelCodes);

            return generateJSONResponseMAV(true, channelInfo, "OK");
        } catch (Exception e) {
            return generateJSONResponseMAV(false, e.getMessage(), "Failed.");
        }
    }

    @RequestMapping("/getTimeseriesUrl.do")
    public ModelAndView getTimeseriesUrl(
        @RequestParam("serviceUrl") String serviceUrl,
        @RequestParam("networkCode") String networkCode,
        @RequestParam("stationCode") String stationCode,
        @RequestParam("channel") String channel,
        @RequestParam("start") String start,
        @RequestParam("duration") String duration,
        @RequestParam("output") String output) {

        serviceUrl = ensureTrailingForwardslash(serviceUrl);
        try {
            Document irisDoc = getDocumentFromURL(
                    serviceUrl + "timeseries/query?net=" + networkCode +
                    "&station=" + stationCode +
                    "&cha=" + channel +
                    "&start=" + start +
                    "&duration=" + duration +
                    "&output=" + output +
                    "&loc=--&ref=xml");

            Node urlNode = irisDoc.getDocumentElement().getElementsByTagName("url").item(0);
            return generateJSONResponseMAV(true, urlNode.getTextContent(), "OK");
        } catch (FileNotFoundException ioe) {
            return generateJSONResponseMAV(false, "404", "Failed: no data was available for the date range specified.");
        } catch (Exception e) {
            return generateJSONResponseMAV(false, e.getMessage(), "Failed.");
        }
    }
}