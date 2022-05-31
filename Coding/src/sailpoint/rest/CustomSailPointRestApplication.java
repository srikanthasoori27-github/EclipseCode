package sailpoint.rest;

public class CustomSailPointRestApplication extends SailPointRestApplication {

  public CustomSailPointRestApplication() {
    super();
    
    register(WorkItemArchiveExtendedResource.class);
  }
}
