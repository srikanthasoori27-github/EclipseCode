package sailpoint.tools.xml;

import java.io.File;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AgsTaskManipulator extends XmlManipulator {
    public static void main( String[] args ) {
        if( args.length != 1 ) {
            showUsage();
        }
        File connectorRegistryFile = new File( args[ 0 ] );
        if( !connectorRegistryFile.exists() ) {
            showUsage();
        }
        AgsTaskManipulator manipulator = new AgsTaskManipulator();
        manipulator.manipulate( connectorRegistryFile );
    }

    public static void showUsage() {
        System.out.println( "AgsTaskManipulator path\\to\\tasks.xml" );
        System.exit( 1 );
    }

    @Override
    protected NodeList getInterestingNodes( Document document ) {
        NodeList nodes = document.getElementsByTagName( "TaskDefinition" );
        return nodes;
    }

    @Override
    protected void processNode( Node node, List<Node> nodesToRemove ) {
        if( isNodeToRemove( node ) ) {
            nodesToRemove.add( node );
        }
    }

    @Override
    protected boolean isNodeToRemove( Node node ) {
        Node executor = getAttribute( node, "executor" );
        if( executor != null ) {
            if( executor.getNodeValue().equals( "sailpoint.connector.nidm.NIDMApplicationGenerator" ) ) {
                return true;
            }
        }
        return false;
    }

}
