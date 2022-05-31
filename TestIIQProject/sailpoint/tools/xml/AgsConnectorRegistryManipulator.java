package sailpoint.tools.xml;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.*;

/**
 * A class to remove things connectors and features from connectorRegistry.xml  
 * that NetIQ does not have a license for, or something.   
 *
 * @author justin.williams
 */
public class AgsConnectorRegistryManipulator extends XmlManipulator {


    public static void main( String[] args ) {
        if( args.length != 1 ) {
            showUsage();
        }
        File connectorRegistryDirectory = new File( args[ 0 ] );
        if( !connectorRegistryDirectory.exists() ) {
            showUsage();
        }
        AgsConnectorRegistryManipulator manipulator = new AgsConnectorRegistryManipulator();
        if(!connectorRegistryDirectory.isDirectory()) {
            throw new RuntimeException("Must be path to connector definitions directory");
        }
        FileFilter xmlFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith("xml");
            }
        };
        for(File connectorFile : connectorRegistryDirectory.listFiles(xmlFilter)) {
            manipulator.manipulate(connectorFile);
        }
        /* Remove applications from master connectorRegistry file */
        File parentFile = connectorRegistryDirectory.getParentFile();
        File connectorRegistry = new File(parentFile, "connectorRegistry.xml");
        XmlManipulator connectorRegistryManipulator = new InternalConnectorRegistryManipulator(manipulator.applicationsToBeRemoved);
        connectorRegistryManipulator.manipulate(connectorRegistry);
    }

    private static class InternalConnectorRegistryManipulator extends XmlManipulator {
        private List<String> applicationsToBeRemoved;

        public InternalConnectorRegistryManipulator(List<String> applicationsToBeRemoved) {
            this.applicationsToBeRemoved = applicationsToBeRemoved;
        }

        @Override
        protected NodeList getInterestingNodes(Document document) {
            NodeList nodes = document.getElementsByTagName("String");
            return nodes;
        }

        @Override
        protected boolean isNodeToRemove(Node node) {
            String connectorFileName = node.getFirstChild().getNodeValue();
            for(String applicationToRemove : applicationsToBeRemoved) {
                if(connectorFileName.endsWith(applicationToRemove)) {
                    return true;
                }
            }
            return false;
        }
    }

    public List<String> applicationsToBeRemoved = new ArrayList<String>();
    private String currentFileName;

    @Override
    public void manipulate(File file) {
        currentFileName = file.getName();
        super.manipulate(file);
    }

    @Override
    protected NodeList getInterestingNodes( Document document ) {
        NodeList nodes = document.getElementsByTagName( "Application" );
        return nodes;
    }

    @Override
    protected boolean isNodeToRemove( Node node ) {
        Node connector = getAttribute( node, "connector" );
        boolean response = false;
        if( connector != null ) {
            if( connector.getNodeValue().equals( "sailpoint.connector.sm.SMConnector" ) ) {
                response = true;
            } else if( connector.getNodeValue().equals( "sailpoint.connector.NovellLdapConnector" ) ) {
                response = true;
            } else if( connector.getNodeValue().equals( "sailpoint.connector.cib.CIBConnector" ) ) {
                response = true;
            }
        }
        if(response) {
            applicationsToBeRemoved.add(currentFileName);
        }
        return response;
    }

    @Override
    protected void processNode( Node node, List<Node> nodesToRemove ) {
        super.processNode( node, nodesToRemove );
        removeProvisioningFeature( node );
    }

    private void removeProvisioningFeature( Node application ) {
        Node featureAttribute = getAttribute( application, "featuresString" );
        if( featureAttribute != null ) {
            String featureString = featureAttribute.getNodeValue();
            if( featureString.contains( "PROVISIONING" ) ) {
                featureString = featureString.replaceAll( "^PROVISIONING\\s*,\\s*", "" );
                featureString = featureString.replaceAll( ",\\s*PROVISIONING\\s*", "" );
                featureString = featureString.replaceAll( "^GROUP_PROVISIONING\\s*,\\s*", "" );
                featureString = featureString.replaceAll( "\\s*,\\s*GROUP_PROVISIONING\\s*", "" );
                featureAttribute.setNodeValue( featureString );
            }
        }
    }

    public static void showUsage() {
        System.out.println( "AgsConnectorRegistryManipulator path\\to\\connectorregistry\\connectoryRegistry.xml" );
        System.exit( 1 );
    }
}
