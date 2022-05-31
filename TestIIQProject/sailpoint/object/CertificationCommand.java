/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import sailpoint.api.ObjectUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A CertificationCommand encapsulates a request to modify a certification
 * in some way. These are intended to be left on the Certification and
 * consumed by the Certificationer during refresh to trigger some behavior,
 * and are used instead of just decorating specific CertificationItems and
 * CertificationIdentities with actions because the command spans many elements
 * in the Certification.
 * 
 * This is a very weak GoF command pattern in that the command itself is not
 * responsible for the execution logic - it merely serves as a trigger with
 * some information about the command to be processed. The reason for this is
 * that all business logic dealing with certifications has been externalized
 * into the Certificationer. If more commands are created it might make
 * sense to move the execution logic into the command class, but this would
 * require the object package to have some interaction with the Certificationer.
 * Not a bad thing really (objects already load other objects
 * through a Resolver interface), but not yet necessary.
 */
@XMLClass
public abstract class CertificationCommand extends AbstractXmlObject {

    private Identity requester;
    private List<String> itemIds;
    private Class<? extends AbstractCertificationItem> itemClass;
    private transient Set<AbstractCertificationItem> unpersistedItems;

    /**
     * Default constructor. 
     */
    public CertificationCommand() {
    }
    
    public CertificationCommand(Identity requester, Class<? extends AbstractCertificationItem> itemClass, List<String> ids) {
        this.requester = requester;
        this.itemClass = itemClass;
        // IIQTC-66 - Use the setter to set ids; eliminates dupes
        setItemIds(ids);
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getRequester() {
        return requester;
    }

    public void setRequester(Identity requester) {
        this.requester = requester;
    }

    @XMLProperty
    public Class<? extends AbstractCertificationItem> getItemClass() {
        return itemClass;
    }

    public void setItemClass(Class<? extends AbstractCertificationItem> itemClass) {
        this.itemClass = itemClass;
    }

    @XMLProperty
    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = new ArrayList();

        // IIQTC-66 - Eliminate duplicate items lest we corrupt the data
        if (!Util.isEmpty(itemIds)) {
            Set<String> itemIdsSet = new LinkedHashSet<String>(itemIds);
            if (itemIdsSet.size() != itemIds.size()) {
                // there's a dupe!
                itemIds = new ArrayList<String>(itemIdsSet);
            }
            this.itemIds.addAll(itemIds);
        }
    }

    /**
     * @exclude
     * @deprecated  Left in for legacy XML.
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST, legacy=true)
    public void setIdentities(List<AbstractCertificationItem> items) {
        this.itemIds = new ArrayList<String>();
        if (!Util.isEmpty(items)) {
            this.itemIds.addAll(ObjectUtil.getObjectIds(items));
            //We have only used this on one type at a time historically, so 
            //safe to get the class from the first items
            this.itemClass = items.get(0).getClass();
        }
    }

    /**
     * @exclude
     * @deprecated  Left in for legacy XML.
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST, legacy=true)
    public void setItems(List<AbstractCertificationItem> items) {
        this.itemIds = new ArrayList<String>();
        if (!Util.isEmpty(items)) {
            this.itemIds.addAll(ObjectUtil.getObjectIds(items));
            //We have only used this on one type at a time historically, so 
            //safe to get the class from the first items
            this.itemClass = items.get(0).getClass();
        }
    }

    /**
     * Merge the given items into the existing items on this command.
     */
    public void mergeItems(Collection<String> itemIds) {
        if (null != itemIds) {
            if (null == this.itemIds) {
                this.itemIds = new ArrayList<String>();
            }

            if (!Util.isEmpty(itemIds)) {
                // Use a LinkedHashSet to filter dups and maintain order.
                Set<String> set =
                        new LinkedHashSet<String>();
                set.addAll(this.itemIds);
                set.addAll(itemIds);
                this.itemIds = new ArrayList<String>(set);
            }
        }
    }
    
    public void remove(String id) {
        this.itemIds.remove(id);
    }
    
    public boolean isEmpty() {
        return (this.itemIds == null || this.itemIds.size() == 0);
    }
    
    /**
     * Find the CertificationCommand that is similar to the given command in the
     * given list.  This returns null if a similar command cannot be found.
     */
    public static CertificationCommand findSimilar(CertificationCommand cmd,
                                                   List<CertificationCommand> cmds) {
        CertificationCommand found = null;
        if (null != cmds) {
            Comparator<CertificationCommand> c = cmd.getSimilarComparator();
            found = Util.find(cmds, cmd, c);
        }
        return found;
    }
    
    public void addUnpersistedItem(AbstractCertificationItem item) {
        if (this.unpersistedItems == null) {
            this.unpersistedItems = new LinkedHashSet<AbstractCertificationItem>();
        }
        this.unpersistedItems.add(item);
    }
    
    public void flushUnpersistedItems() throws GeneralException {
        if (this.unpersistedItems != null && !this.unpersistedItems.isEmpty()) {
            for (AbstractCertificationItem item : this.unpersistedItems) {
                if (item.getId() == null) {
                    throw new FlushUnPersistedCommandsException("Can only flush after all items are persisted.");
                }
                if (this.itemIds == null) {
                    this.itemIds = new ArrayList<String>();
                }
                this.itemIds.add(item.getId());
                if (this.itemClass == null) {
                    this.itemClass = item.getClass();
                }
            }
            this.unpersistedItems.clear();
            this.unpersistedItems = null;
        }
    }

    /**
     * Find the specified unpersisted item. This is useful for finding items
     * that need to be reassigned, but have no ID yet.
     * @param item  The AbstractCertificationItem to find.
     * @return The matching unpersisted AbstractCertificationItem, or null if
     *    a matching item cannot be found.
     */
    public AbstractCertificationItem findUnpersistedItem(AbstractCertificationItem item) {
        boolean findingCertItem = item instanceof CertificationItem;
        boolean findingCertEntity = item instanceof CertificationEntity;

        for (AbstractCertificationItem ghostItem : this.unpersistedItems) {
            if (ghostItem instanceof CertificationEntity) {
                CertificationEntity certEnt = (CertificationEntity) ghostItem;
                if (findingCertEntity && ((CertificationEntity) item).equals(certEnt)) {
                    return ghostItem;
                } else if (findingCertItem) {

                    CertificationItem foundItem = Util.findComparable(certEnt.getItems(), (CertificationItem) item, false);
                    if (null != foundItem) {
                        return foundItem;
                    }
                }
            } else if (ghostItem instanceof CertificationItem) {
                CertificationItem certItem = (CertificationItem) ghostItem;
                if (findingCertItem && ((CertificationItem) item).compareTo(certItem) == 0) {
                    return ghostItem;
                }

                // and if you're looking for a certEnt and this unpersistedThing is a certItem, I can't help you.
                // maybe I could look at unpersistedThing's parent.

            }
        }

        // didn't find anything
        return null;
    }

    /**
     * Return a Comparator that will match a "similar" command. This is used
     * when determining which commands can be merged.
     */
    protected Comparator<CertificationCommand> getSimilarComparator() {
        return new Comparator<CertificationCommand>() {
            public int compare(CertificationCommand o1, CertificationCommand o2) {
                // We're comparing apples and oranges ... not the same.
                if (!o1.getClass().equals(o2.getClass())) {
                    return -1;
                }
                
                // Make sure it is the same class
                if (!Util.nullSafeEq(o1.itemClass, o2.itemClass, true)) {
                    return -1;
                }
                
                // Don't look at the items.
                if (!Util.nullSafeEq(o1.requester, o2.requester, true))
                    return -1;
                return 0;
            }
        };
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        CertificationCommand c = (CertificationCommand) obj;

        Comparator<CertificationCommand> compare = getSimilarComparator();
        if (compare.compare(this, c) != 0) {
            return false;
        }

        // At this point we know the objects are similar, but we don't know if they're equal.
        // Need to check a few more things...
        List<String> items1 = this.getItemIds();
        List<String> items2 = c.getItemIds();

        return Util.nullSafeEq(this.unpersistedItems, c.unpersistedItems, true) &&
                ((items1 == null && items2 == null) || Util.orderInsensitiveEquals(items1, items2));
    }

    @Override
    public int hashCode() {
        return Objects.hash(requester, itemIds, itemClass, unpersistedItems);
    }

    /**
     * A concrete CertificationCommand that request a bulk re-assignment of
     * multiple CertificationIdentities.
     */
    @XMLClass
    public static class BulkReassignment extends CertificationCommand {
        private Identity recipient;
        private String certificationName;
        private String description;
        private String comments;
        private boolean checkSelfCertification;
        private boolean checkLimitReassignments;
        private boolean selfCertificationReassignment;

        public BulkReassignment() {
            super();
        }

        public BulkReassignment(Identity requester,
                                Class<? extends AbstractCertificationItem> itemClass,
                                List<String> itemIds,
                                Identity recipient, String description,
                                String comments) {
            this(requester, itemClass, itemIds, recipient, null, description, comments);
        }

        public BulkReassignment(Identity requester,
                                Class<? extends AbstractCertificationItem> itemClass,
                                List<String> itemIds,
                                Identity recipient, String certificationName,
                                String description, String comments) {
            this(requester, itemClass, itemIds, recipient, certificationName, description, comments, false);
        }

        public BulkReassignment(Identity requester,
                                Class<? extends AbstractCertificationItem> itemClass,
                                List<String> itemIds,
                                Identity recipient, String certificationName,
                                String description, String comments, boolean checkSelfCertification) {
            this(requester, itemClass, itemIds, recipient, certificationName, description, comments, checkSelfCertification, false);
        }

        public BulkReassignment(Identity requester,
                                Class<? extends AbstractCertificationItem> itemClass,
                                List<String> itemIds,
                                Identity recipient, String certificationName,
                                String description, String comments, 
                                boolean checkSelfCertification, boolean checkLimitReassignments) {
            super(requester, itemClass, itemIds);
            this.recipient = recipient;
            this.certificationName = certificationName;
            this.description = description;
            this.comments = comments;
            this.checkSelfCertification = checkSelfCertification;
            this.checkLimitReassignments = checkLimitReassignments;
        }

        /**
         * Copy constuctor.
         * @param br
         */
        public BulkReassignment(BulkReassignment br) {
            this(
                    br.getRequester(),
                    br.getItemClass(),
                    br.getItemIds(),
                    br.getRecipient(),
                    br.getCertificationName(),
                    br.getDescription(),
                    br.getComments(),
                    br.isCheckSelfCertification(),
                    br.isCheckLimitReassignments()
            );
        }

        @XMLProperty
        public String getCertificationName() {
            return this.certificationName;
        }
        
        public void setCertificationName(String certificationName) {
            this.certificationName = certificationName;
        }
        
        @XMLProperty
        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }

        @XMLProperty
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE)
        public Identity getRecipient() {
            return recipient;
        }

        public void setRecipient(Identity recipient) {
            this.recipient = recipient;
        }

        @XMLProperty
        public boolean isCheckSelfCertification() {
            return this.checkSelfCertification;
        }

        public void setCheckSelfCertification(boolean checkSelfCertification) {
            this.checkSelfCertification = checkSelfCertification;
        }

        @XMLProperty
        public boolean isSelfCertificationReassignment() {
            return this.selfCertificationReassignment;
        }

        public void setSelfCertificationReassignment(boolean selfCertificationReassignment) {
            this.selfCertificationReassignment = selfCertificationReassignment;
        }

        @XMLProperty
        public boolean isCheckLimitReassignments() {
            return this.checkLimitReassignments;
        }

        public void setCheckLimitReassignments(boolean checkLimitReassignments) {
            this.checkLimitReassignments = checkLimitReassignments;
        }
        
        protected Comparator<CertificationCommand> getSimilarComparator() {
            final Comparator<CertificationCommand> parent =
                super.getSimilarComparator();
            return new Comparator<CertificationCommand>() {
                public int compare(CertificationCommand o1, CertificationCommand o2) {
                    int parentResult = parent.compare(o1, o2);
                    if (0 != parentResult)
                        return parentResult;

                    // Don't check comments or description.  These are likely to
                    // vary slightly if generated by a pre-delegation rule, but
                    // not enough to warrant an entirely new command (which
                    // would produce an additional email notification).
                    BulkReassignment r1 = (BulkReassignment) o1;
                    BulkReassignment r2 = (BulkReassignment) o2;
                    if (!Util.nullSafeEq(r1.recipient, r2.recipient, true))
                        return -1;

                    return 0;
                }
            };
        }

        @Override
        public boolean equals(Object obj) {

            if (!super.equals(obj)) {
                return false;
            }

            BulkReassignment br = (BulkReassignment) obj;

            return Util.nullSafeEq(this.getRecipient(), br.getRecipient(), true) &&
                    Util.nullSafeEq(this.getDescription(), br.getDescription(), true) &&
                    Util.nullSafeEq(this.getComments(), br.getComments(), true) &&
                    Util.nullSafeEq(this.getCertificationName(), br.getCertificationName(), true) &&
                    this.isCheckLimitReassignments() == br.isCheckLimitReassignments() &&
                    this.isCheckSelfCertification() == br.isCheckSelfCertification() &&
                    this.isSelfCertificationReassignment() == br.isSelfCertificationReassignment();
        }

        @Override
        public int hashCode() {
            return super.hashCode() +
                    Objects.hash(recipient, certificationName, description, comments, checkSelfCertification, checkLimitReassignments, selfCertificationReassignment);
        }
    }

    /**
     * This is an exception that will get thrown if there are commands
     * that are in queue to be merged, but have not been persisted yet.
     *
     * This exception is broken out of GeneralException because in some
     * cases it represents a problem that does not need to be worried about.  So
     * this is an attempt to be more specific about which exceptions are
     * allowed.
     *
     */
    public static class FlushUnPersistedCommandsException extends GeneralException {
        FlushUnPersistedCommandsException() {
            super();
        }

        FlushUnPersistedCommandsException(String s) {
            super(s);
        }
    }
}
