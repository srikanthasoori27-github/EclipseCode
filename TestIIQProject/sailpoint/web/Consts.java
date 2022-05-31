package sailpoint.web;

public final class Consts {

    public static final class Headers {
        public static final String REFERER = "referer";
    }

    public static enum NavigationString {
        edit,
        viewIdentity,
        //IIQETN-5464
        viewIdentityDetails,
        viewCertifications,
        managePasswords,
        manageAccounts,
        manageAttributes,
        requestAccess,
        createIdentity,
        editIdentity,
        manageWorkItems,
        viewApprovals
    }
}