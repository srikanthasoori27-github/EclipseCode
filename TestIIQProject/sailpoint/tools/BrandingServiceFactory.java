package sailpoint.tools;

import sailpoint.server.Environment;


public abstract class BrandingServiceFactory {
    
    private static BrandingService brandingService;
    private static Object mutex = new Object();
    
    public static BrandingService getService() {
        synchronized( mutex ) {
            if( brandingService == null ) {
                brandingService = initializeBrandingService();
            }
        }
        return brandingService;
    }
    
    private static BrandingService initializeBrandingService() {
        if( getBrand() == Brand.AGS ) {
            return new NetIqBrandingService();
        }
        return new SailPointBrandingService();
    }
    
    private static Brand getBrand() {
        /* Check for ags branding system property */
        String brand = System.getProperty( "branding" );
        if( isAgsBrand( brand ) ) {
            return Brand.AGS;
        }
        /* Check for ags branding */
        Environment environment = Environment.getEnvironment();
        if( environment != null ) {
            brand = environment.getBranding();
            if( isAgsBrand( brand ) ) {
                return Brand.AGS;
            }
        }
        /* Last ditch check for property file */
        String path = Util.getResourcePath( "iiq.properties" );
        if( path != null ) {
            return Brand.IIQ;
        }
        path = Util.getResourcePath( "ags.properties" );
        if( path != null ) {
            return Brand.AGS;
        }
        throw new IllegalStateException( "Unable to determine branding" );
    }

    private static boolean isAgsBrand( String brand ) {
        if( brand != null ) {
            if( brand.equals( "ags" ) ) {
                return true;
            }
        }
        return false;
    }
 
}
