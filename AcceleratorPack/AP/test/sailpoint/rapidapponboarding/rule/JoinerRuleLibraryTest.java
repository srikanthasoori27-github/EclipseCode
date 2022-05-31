/* 
 Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
 All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
 that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
 and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc. 
*/
package sailpoint.rapidapponboarding.rule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
/**
 * Test class for JoinerRuleLibrary
 * 
 * @author rahul.srivastava
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ BrandingServiceFactory.class, WrapperRuleLibrary.class, BrandingService.class,
		JoinerRuleLibrary.class, IdentityService.class, ROADUtil.class, AttributeSyncRuleLibrary.class })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class JoinerRuleLibraryTest {
	@Mock
	private SailPointContext spc;
	@Mock
	private BrandingService brandingService;
	@Mock
	private Custom custom;
	private Identity previousIdentity;
	private Identity newIdentity;
	private static final String TRUE = "TRUE";
	private static final String IDENTITY_NAME = "james.smith";
	private static final String BUNDLE_NAME = "bName";
	private static final String APPLICATION_NAME = "Active Directory";
	private static final String JOINERROLES = "joinerRoles";
	private static final String JOINERROLES_VALUE = "joinerRolesValue";
	private static final String JOINERPOPREGEX = "joinerPopulationRegex";
	private static final String JOINERPOPREGEX_VALUE = "ADDirect";
	private static final String JOINERTOKEN = "#IIQJoiner#";
	private static final String POPULATION_NAME = "austinPop";
	private static final String POPULATION_MATCHED = "Population Matched";
	private static final String CUBE_ATTR = "Cube Attribute";
	private static final String ROLE_NAME = "Account Manager";
	private static final String ATT_DEF__NAME = "Att_Def_Name";
	private static final String ATT_VALUE_PP_FORM = "ProvisiongFormValue";
	/**
	 * Performs initialization
	 * 
	 * @throws Exception
	 */
	@Before
	public void setUp() throws Exception {
		PowerMockito.mockStatic(BrandingServiceFactory.class);
		PowerMockito.spy(JoinerRuleLibrary.class);
		Mockito.when(brandingService.getAdminUserName()).thenReturn("Sailpoint");
		Mockito.when(BrandingServiceFactory.getService()).thenReturn(brandingService);
		PowerMockito.mockStatic(WrapperRuleLibrary.class);
		previousIdentity = new Identity();
		newIdentity = new Identity();
	}
	/**
	 * Performs cleanup
	 * 
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		spc = null;
		custom = null;
		brandingService = null;
		previousIdentity = null;
		newIdentity = null;
	}
	/**
	 * Method to check if an identity is eligible for Joiner
	 * 
	 * Test condition: The identity falls under the re-hire category
	 * 
	 * Expected Results: The identity is not eligible for joiner as it's eligible
	 * for re-hire
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForJoiner_Rehire() throws Exception {
		newIdentity.setAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS, this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.isPersonaEnabled(spc)).thenReturn(TRUE);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERREHIREPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.getRelationshipChangesPersona(spc, newIdentity, previousIdentity, false))
				.thenReturn(this.getRelationShipChanges());
		Mockito.when(spc.getObjectByName(Custom.class, "Custom-Persona-Settings")).thenReturn(custom);
		boolean flag = JoinerRuleLibrary.isEligibleForJoiner(spc, previousIdentity, newIdentity);
		assertFalse("The joiner can be kicked of as all the criteria is valid", flag);
	}
	/**
	 * Method to check if an identity is eligible for Joiner
	 * 
	 * Test condition: The identity's trigger is invalid
	 * 
	 * Expected Results: The identity is not eligible for joiner as it's as one the
	 * trigger is invalid
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForJoiner_InvalidTrigger() throws Exception {
		Mockito.when(WrapperRuleLibrary.isPersonaEnabled(spc)).thenReturn(TRUE);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERREHIREPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERPROCESS)).thenReturn(false);
		Mockito.when(WrapperRuleLibrary.getRelationshipChangesPersona(spc, newIdentity, previousIdentity, false))
				.thenReturn(this.getRelationShipChanges());
		Mockito.when(spc.getObjectByName(Custom.class, "Custom-Persona-Settings")).thenReturn(custom);
		boolean flag = JoinerRuleLibrary.isEligibleForJoiner(spc, previousIdentity, newIdentity);
		assertFalse("The joiner can be kicked of as all the criteria is valid", flag);
	}
	/**
	 * Method to check if an identity is eligible for Joiner
	 * 
	 * Test condition: The identity does not match the all new relationship persona
	 * Expected Results: The identity is not eligible for joiner as it's as one the
	 * trigger is invalid
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForJoiner_AllNewRelationshipInvalid() throws Exception {
		newIdentity.setAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS, this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.isPersonaEnabled(spc)).thenReturn(TRUE);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERREHIREPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.getRelationshipChangesPersona(spc, newIdentity, previousIdentity, false))
				.thenReturn(this.getRelationShipChanges());
		PowerMockito.doReturn(false).when(JoinerRuleLibrary.class, "checkAllNewRelationshipsPersona", spc, newIdentity,
				previousIdentity);
		Mockito.when(spc.getObjectByName(Custom.class, "Custom-Persona-Settings")).thenReturn(custom);
		boolean flag = JoinerRuleLibrary.isEligibleForJoiner(spc, previousIdentity, newIdentity);
		assertFalse("The Joiner has been kicked off", flag);
	}
	/**
	 * Method to check if an identity is eligible for Joiner
	 * 
	 * Test condition: The identity does not match the all new relationship persona
	 * Expected Results: The identity is not eligible for joiner as it's as one the
	 * trigger is invalid
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForJoiner_AllNewRelationshipValid() throws Exception {
		newIdentity.setAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS, this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.isPersonaEnabled(spc)).thenReturn(TRUE);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERREHIREPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.getRelationshipChangesPersona(spc, newIdentity, previousIdentity, false))
				.thenReturn(this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.checkIsNewRelationshipPersona(spc, newIdentity, previousIdentity))
				.thenReturn(true);
		PowerMockito.doReturn(true).when(JoinerRuleLibrary.class, "checkAllNewRelationshipsPersona", spc, newIdentity,
				previousIdentity);
		Mockito.when(JoinerRuleLibrary.isEligibleForRehire(spc, previousIdentity, newIdentity)).thenReturn(false);
		Mockito.when(spc.getObjectByName(Custom.class, "Custom-Persona-Settings")).thenReturn(custom);
		boolean flag = JoinerRuleLibrary.isEligibleForJoiner(spc, previousIdentity, newIdentity);
		assertTrue("The Joiner cannot be kicked off as all relationships are not valid", flag);
	}
	/**
	 * Method to check if an identity is eligible for Joiner
	 * 
	 * Test condition: The identity is a new identity as previousIdentity does not
	 * exists
	 * 
	 * Expected Results: The identity is eligible for joiner previousIdentity is
	 * null
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForJoiner_IsANewIdentity() throws Exception {
		previousIdentity = null;
		newIdentity.setAttribute(WrapperRuleLibrary.PERSONARELATIONSHIPS, this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.isPersonaEnabled(spc)).thenReturn(TRUE);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERREHIREPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.validateTriggersBeforePersona(spc, newIdentity, previousIdentity,
				JoinerRuleLibrary.JOINERPROCESS)).thenReturn(true);
		Mockito.when(WrapperRuleLibrary.getRelationshipChangesPersona(spc, newIdentity, previousIdentity, false))
				.thenReturn(this.getRelationShipChanges());
		Mockito.when(WrapperRuleLibrary.checkIsNewRelationshipPersona(spc, newIdentity, previousIdentity))
				.thenReturn(false);
		PowerMockito.spy(JoinerRuleLibrary.class);
		PowerMockito.doReturn(false).when(JoinerRuleLibrary.class, "checkAllNewRelationshipsPersona", spc, newIdentity,
				previousIdentity);
		Mockito.when(spc.getObjectByName(Custom.class, "Custom-Persona-Settings")).thenReturn(custom);
		boolean flag = JoinerRuleLibrary.isEligibleForJoiner(spc, previousIdentity, newIdentity);
		assertTrue("The joiner cannot be kicked off as it's not a new identity", flag);
	}
	/**
	 * Method to check if an identity is eligible for RWLTD
	 * 
	 * Test condition: The identity is a new identity as previousIdentity does not
	 * exists
	 * 
	 * Expected Results: The identity is not eligible RTWLTD
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForRTWLTD() throws Exception {
		assertFalse("", JoinerRuleLibrary.isEligibleForRTWLTD(spc, previousIdentity, newIdentity));
	}
	/**
	 * Method to check if an identity is eligible for RTWLOA
	 * 
	 * Test condition: The identity is a new identity as previousIdentity does not
	 * exists
	 * 
	 * Expected Results: The identity is not RTWLOA
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsEligibleForRTWLOA() throws Exception {
		assertFalse("", JoinerRuleLibrary.isEligibleForRTWLOA(spc, previousIdentity, newIdentity));
	}
	/**
	 * Test method for getRolesForJoinerApplication()
	 * 
	 * Test condition: No application is associated with the expected application
	 * name
	 * 
	 * Expected Results: The list of associated roles in empty
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetRolesForJoinerApplication_NoApplicationAssociated() throws Exception {
		Identity identity = this.getIdentity(true);
		newIdentity.setName(IDENTITY_NAME);
		IdentityService identityService = Mockito.mock(IdentityService.class);
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		PowerMockito.whenNew(IdentityService.class).withArguments(spc).thenReturn(identityService);
		List<String> joinerRoles = JoinerRuleLibrary.getRolesForJoinerApplication(spc, newIdentity, "AD");
		assertTrue("Joiner roles have been evaluated", joinerRoles.size() == 0);
	}
	/**
	 * Test method for getRolesForJoinerApplication()
	 * 
	 * Test condition: Invalid identity is passed to get the joiner roles
	 * 
	 * Expected Results: The list of associated roles in empty
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetRolesForJoinerApplication_InvalidIdentity() throws Exception {
		List<String> joinerRoles = JoinerRuleLibrary.getRolesForJoinerApplication(spc, null, "AD");
		assertTrue("Joiner roles have been evaluated", joinerRoles.size() == 0);
	}
	/**
	 * Test method for getRolesForJoinerApplication()
	 * 
	 * Test condition: No application is associated with the expected application
	 * name
	 * 
	 * Expected Results: The list of associated roles in empty
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetRolesForJoinerApplication_NoExistingRolesInIdentity() throws Exception {
		Identity identity = this.getIdentity(false);
		newIdentity.setName(IDENTITY_NAME);
		IdentityService identityService = Mockito.mock(IdentityService.class);
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		PowerMockito.whenNew(IdentityService.class).withArguments(spc).thenReturn(identityService);
		List<String> joinerRoles = JoinerRuleLibrary.getRolesForJoinerApplication(spc, newIdentity, "AD");
		assertTrue("Joiner roles have been evaluated", joinerRoles.size() == 0);
	}
	/**
	 * Test method for getRolesForJoinerApplication()
	 * 
	 * Test condition: application is associated with the expected application name
	 * and Identity has a bithright role
	 * 
	 * Expected Results: The associated role list is not empty
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetRolesForJoinerApplication_ApplicationAssociatedToIdentityAndIsBirthRightRole() throws Exception {
		Identity identity = this.getIdentity(true);
		IdentityService identityService = Mockito.mock(IdentityService.class);
		newIdentity.setName(IDENTITY_NAME);
		this.mockIterator();
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		Mockito.when(spc.getObjectByName(Application.class, APPLICATION_NAME)).thenReturn(this.getApplication(true));
		PowerMockito.whenNew(IdentityService.class).withArguments(spc).thenReturn(identityService);
		List<String> joinerRoles = JoinerRuleLibrary.getRolesForJoinerApplication(spc, newIdentity, APPLICATION_NAME);
		assertTrue("Joiner roles have been evaluated and no roles exist", joinerRoles.size() > 0);
		assertEquals("Joiner role expected value does not match", JOINERROLES_VALUE, joinerRoles.get(0));
	}
	/**
	 * Test method for isRoleBirthright()
	 * 
	 * Test condition: The population matches the expected regex
	 * 
	 * Expected Results: True is returned indicating birth right role
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsRoleBirthright() throws Exception {
		this.mockIterator();
		List<String> matchedPopulations = new ArrayList<>();
		boolean flag = JoinerRuleLibrary.isRoleBirthright(spc, BUNDLE_NAME, matchedPopulations, JOINERTOKEN);
		assertTrue("Is a valid birth right role", flag);
	}
	/**
	 * Test method for isRoleBirthright()
	 * 
	 * Test condition: No matching birth right filter
	 * 
	 * Expected Results: false is returned indicating no birth right role
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsRoleBirthright_NoMatchBirthrightFilter() throws Exception {
		List<String> matchedPopulations = new ArrayList<>();
		boolean flag = JoinerRuleLibrary.isRoleBirthright(spc, BUNDLE_NAME, matchedPopulations, JOINERTOKEN);
		assertFalse("Is a valid birth right role", flag);
	}
	/**
	 * Test method for isRoleBirthright()
	 * 
	 * Test condition: No identity selector filter exists
	 * 
	 * Expected Results: false is returned indicating no birth right role
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsRoleBirthright_NoMatchingObjectWithBundleName() throws Exception {
		this.mockIterator();
		IdentitySelector ideSelector = new IdentitySelector();
		GroupDefinition gdp = new GroupDefinition();
		gdp.setName(BUNDLE_NAME);
		ideSelector.setPopulation(gdp);
		List<String> matchedPopulations = new ArrayList<>();
		matchedPopulations.add(BUNDLE_NAME);
		Bundle role = new Bundle();
		role.setSelector(ideSelector);
		Mockito.when(spc.getObjectByName(Bundle.class, APPLICATION_NAME)).thenReturn(role);
		boolean flag = JoinerRuleLibrary.isRoleBirthright(spc, BUNDLE_NAME, matchedPopulations, JOINERTOKEN);
		assertFalse("Is a valid birth right role", flag);
	}
	/**
	 * Test method for isRoleBirthright()
	 * 
	 * Test condition: The identity and birthright population matches
	 * 
	 * Expected Results: false is returned indicating no birth right role
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsRoleBirthright_MatchingPopulationBundle() throws Exception {
		this.mockIterator();
		IdentitySelector ideSelector = new IdentitySelector();
		GroupDefinition gdp = new GroupDefinition();
		gdp.setName(BUNDLE_NAME);
		ideSelector.setPopulation(gdp);
		List<String> matchedPopulations = new ArrayList<>();
		matchedPopulations.add(BUNDLE_NAME);
		Bundle role = new Bundle();
		role.setSelector(ideSelector);
		Mockito.when(spc.getObjectByName(Bundle.class, BUNDLE_NAME)).thenReturn(role);
		boolean flag = JoinerRuleLibrary.isRoleBirthright(spc, BUNDLE_NAME, matchedPopulations, JOINERTOKEN);
		assertTrue("Not a birth right role", flag);
	}
	/**
	 * Test method for isRoleBirthright()
	 * 
	 * Test condition: No matching birth right filter
	 * 
	 * Expected Results: false is returned indicating no birth right role
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsRoleBirthright_PopulationMismatch() throws Exception {
		this.mockIterator();
		IdentitySelector ideSelector = new IdentitySelector();
		GroupDefinition gdp = new GroupDefinition();
		gdp.setName(APPLICATION_NAME);
		ideSelector.setPopulation(gdp);
		List<String> matchedPopulations = new ArrayList<>();
		matchedPopulations.add(BUNDLE_NAME);
		Bundle role = new Bundle();
		role.setSelector(ideSelector);
		Mockito.when(spc.getObjectByName(Bundle.class, BUNDLE_NAME)).thenReturn(role);
		boolean flag = JoinerRuleLibrary.isRoleBirthright(spc, BUNDLE_NAME, matchedPopulations, JOINERTOKEN);
		assertFalse("Not a birth right role", flag);
	}
	/**
	 * Test method for getListofMatchedIdentityPopulations()
	 * 
	 * Test condition: Matching populations exist
	 * 
	 * Expected Results: The list of matching population is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetListofMatchedIdentityPopulations() throws Exception {
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, POPULATION_NAME)).thenReturn(gdp);
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		List<String> matchedIdentities = JoinerRuleLibrary.getListofMatchedIdentityPopulations(spc, POPULATION_NAME,
				newIdentity);
		assertTrue("No Matching identities found", matchedIdentities.size() > 0);
		assertEquals("The population name do not match", POPULATION_NAME, matchedIdentities.get(0));
	}
	private GroupDefinition setGroupDefinitionWithFilter() {
		GroupDefinition gdp = new GroupDefinition();
		gdp.setName(POPULATION_NAME);
		Filter filter = Mockito.mock(Filter.class);
		gdp.setFilter(filter);
		return gdp;
	}
	/**
	 * Test method for getListofMatchedIdentityPopulations()
	 * 
	 * Test condition: No filter exits for the group definition
	 * 
	 * Expected Results: The list of matching population is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void getListofMatchedIdentityPopulations_InvalidGroupFilter() throws Exception {
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, POPULATION_NAME)).thenReturn(gdp);
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(0);
		List<String> matchedIdentities = JoinerRuleLibrary.getListofMatchedIdentityPopulations(spc, POPULATION_NAME,
				newIdentity);
		assertTrue("Matching identities found", matchedIdentities.size() == 0);
	}
	/**
	 * Test method for getAllJoinerApplications()
	 * 
	 * Test condition: Valid joiner applications exist
	 * 
	 * Expected Results: The list of matching population is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetAllJoinerApplications() throws Exception {
		this.mockIterator();
		List<String> joinerApplications = JoinerRuleLibrary.getAllJoinerApplications(spc);
		assertTrue("No joiner application exits", joinerApplications.size() > 0);
		assertEquals("The joiner application name do not match", BUNDLE_NAME, joinerApplications.get(0));
	}
	/**
	 * Test method for getAllJoinerApplications()
	 * 
	 * Test condition: Valid joiner applications exist
	 * 
	 * Expected Results: The list of matching population is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetAllJoinerApplications_NoAppExist() throws Exception {
		List<String> joinerApplications = JoinerRuleLibrary.getAllJoinerApplications(spc);
		assertTrue("Joiner application exits", joinerApplications.size() == 0);
	}
	/**
	 * Test method for getApplicationsForJoiner()
	 * 
	 * Test condition: Joiner has valid application associated matching to the
	 * attribute
	 * 
	 * Expected Results: The list of application associated to the joiner is
	 * returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetApplicationsForJoiner() throws Exception {
		this.mockIterator();
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.getObjectById(Application.class, BUNDLE_NAME)).thenReturn(this.getApplication(true));
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		List<String> joinerApplications = JoinerRuleLibrary.getApplicationsForJoiner(spc, newIdentity);
		assertTrue("No joiner application exits", joinerApplications.size() > 0);
		assertEquals("Unexpected name for the joiner application", APPLICATION_NAME, joinerApplications.get(0));
	}
	/**
	 * Test method for getApplicationsForJoiner()
	 * 
	 * Test condition: No population match for joiner application
	 * 
	 * Expected Results: The list of application associated to the joiner is
	 * returned is empty
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetApplicationsForJoiner_NoMatchingJoinerPopulation() throws Exception {
		this.mockIterator();
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.getObjectById(Application.class, BUNDLE_NAME)).thenReturn(this.getApplication(true));
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(0);
		List<String> joinerApplications = JoinerRuleLibrary.getApplicationsForJoiner(spc, newIdentity);
		assertTrue("Joiner application exits", joinerApplications.size() == 0);
	}
	/**
	 * Test method for getApplicationsForJoiner()
	 * 
	 * Test condition: No regex match for joiner population attribute
	 * 
	 * Expected Results: The list of application associated to the joiner is
	 * returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetApplicationsForJoiner_NoPopRegexMatch() throws Exception {
		this.mockIterator();
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.getObjectById(Application.class, BUNDLE_NAME)).thenReturn(this.getApplication(false));
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		List<String> joinerApplications = JoinerRuleLibrary.getApplicationsForJoiner(spc, newIdentity);
		assertTrue("No joiner application exits", joinerApplications.size() > 0);
		assertEquals("Unexpected name for the joiner application", APPLICATION_NAME, joinerApplications.get(0));
	}
	/**
	 * Test method for getApplicationsForJoiner()
	 * 
	 * Test condition: No applications are SailPointContext
	 * 
	 * Expected Results: Empty list is return
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetApplicationsForJoiner_NoApplicationExistForJoiner() throws Exception {
		List<String> joinerApplications = JoinerRuleLibrary.getApplicationsForJoiner(spc, newIdentity);
		assertTrue("Joiner application exits", joinerApplications.size() == 0);
	}
	/**
	 * Test method for isPopulationMatched()
	 * 
	 * Test condition: No population match is returned
	 * 
	 * Expected Results: Null object is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsPopulationMatched_NoMatcExists() throws Exception {
		String joinerRegularAttrExpression = JOINERTOKEN;
		String popMatched = JoinerRuleLibrary.isPopulationMatched(spc, joinerRegularAttrExpression, newIdentity);
		assertNull("", popMatched);
	}
	/**
	 * Test method for isPopulationMatched()
	 * 
	 * Test condition: Group definition exists for the population name
	 * 
	 * Expected Results: Null object is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsPopulationMatched_ValidGroupDefinitionForPopulationExists() throws Exception {
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		String joinerRegularAttrExpression = JOINERTOKEN;
		String popMatched = JoinerRuleLibrary.isPopulationMatched(spc, joinerRegularAttrExpression, newIdentity);
		assertEquals("Incorrect population matched", POPULATION_MATCHED, popMatched);
	}
	/**
	 * Test method for isPopulationMatched()
	 * 
	 * Test condition: Valid joiner expression attribute exists with valid Cube
	 * values for the identity
	 * 
	 * Expected Results: The expected population match is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsPopulationMatched_ValidJoinerPopulationAndCubeValue() throws Exception {
		newIdentity.setAttribute(POPULATION_NAME, JOINERPOPREGEX_VALUE);
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		String joinerRegularAttrExpression = POPULATION_NAME + JOINERTOKEN + JOINERPOPREGEX_VALUE;
		String popMatched = JoinerRuleLibrary.isPopulationMatched(spc, joinerRegularAttrExpression, newIdentity);
		assertEquals("Incorrect population matched", JOINERPOPREGEX_VALUE, popMatched);
	}
	/**
	 * Test method for isPopulationMatched()
	 * 
	 * Test condition: Valid joiner expression attribute exists for the identity
	 * 
	 * Expected Results: The expected population match is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testIsPopulationMatched_ValidJoinerPopulationAndInvalidCube() throws Exception {
		newIdentity.setAttribute(POPULATION_NAME, JOINERPOPREGEX_VALUE);
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		String joinerRegularAttrExpression = CUBE_ATTR + JOINERTOKEN + JOINERPOPREGEX_VALUE;
		String popMatched = JoinerRuleLibrary.isPopulationMatched(spc, joinerRegularAttrExpression, newIdentity);
		assertEquals("Incorrect population matched", null, popMatched);
	}
	/**
	 * Test method for addToProvisioningPlanIIQ()
	 * 
	 * Test condition: Valid identityName and role name exists
	 * 
	 * Expected Results: The list of provisions are returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAddToProvisioningPlanIIQ() {
		List<AccountRequest> allRequests = JoinerRuleLibrary.addToProvisioningPlanIIQ(IDENTITY_NAME, ROLE_NAME);
		assertTrue("The provision list is empty", allRequests.size() > 0);
	}
	/**
	 * Test method for restoreBirthrightBundles()
	 * 
	 * Test condition: Valid identityName and app name exists with roles for joiner
	 * application
	 * 
	 * Expected Results: The birth role list is returned for reverse leaver
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRestoreBirthrightBundles() throws Exception {
		this.mockIterator();
		Identity identity = this.getIdentity(true);
		newIdentity.setName(IDENTITY_NAME);
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		GroupDefinition gdp = this.setGroupDefinitionWithFilter();
		Mockito.when(spc.getObjectByName(GroupDefinition.class, JOINERTOKEN)).thenReturn(gdp);
		Mockito.when(spc.getObjectById(Application.class, BUNDLE_NAME)).thenReturn(this.getApplication(true));
		Mockito.when(spc.countObjects(Mockito.any(), Mockito.any())).thenReturn(1);
		Mockito.when(spc.getObjectByName(Application.class, APPLICATION_NAME)).thenReturn(this.getApplication(true));
		List<AccountRequest> allRequests = JoinerRuleLibrary.restoreBirthrightBundles(spc, newIdentity,
				APPLICATION_NAME);
		assertTrue("The provision list is empty", allRequests.size() > 0);
	}
	/**
	 * Test method for restoreBirthrightBundles()
	 * 
	 * Test condition: Valid identityName and app name exists no roles for joiner
	 * application
	 * 
	 * Expected Results: Any empty list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRestoreBirthrightBundles_NoRolesExist() throws Exception {
		Identity identity = this.getIdentity(false);
		newIdentity.setName(IDENTITY_NAME);
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		Mockito.when(spc.getObjectByName(Application.class, APPLICATION_NAME)).thenReturn(this.getApplication(true));
		List<AccountRequest> allRequests = JoinerRuleLibrary.restoreBirthrightBundles(spc, newIdentity,
				APPLICATION_NAME);
		assertTrue("The provision list is not empty", allRequests.size() == 0);
	}
	/**
	 * Test method for createNewAccountProvisioningPlan()
	 * 
	 * Test condition: Valid identityName and app name exists no roles for joiner
	 * application
	 * 
	 * Expected Results: List of provisioning plan is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateNewAccountProvisioningPlan() throws Exception {
		this.mockDataForAccountProvisioning(0);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createNewAccountProvisioningPlan(spc, APPLICATION_NAME,
				newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() > 0);
		assertEquals("The operation name do not match", AccountRequest.Operation.Create,
				allRequests.get(0).getOperation());
	}
	/**
	 * Test method for createNewAccountProvisioningPlan()
	 * 
	 * Test condition: Invalid native identity for the application name
	 * 
	 * Expected Results: Any empty list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateNewAccountProvisioningPlan_EmptyList() throws Exception {
		this.mockDataForAccountProvisioning(1);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createNewAccountProvisioningPlan(spc, APPLICATION_NAME,
				newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() == 0);
	}
	/**
	 * Test method for createOrUpdateNewAccountProvisioningPlanExternalRepo()
	 * 
	 * Test condition: Valid identityName and app name exists with valid native Id
	 * 
	 * Expected Results: List of Account plan is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateOrUpdateNewAccountProvisioningPlanExternalRepo() throws Exception {
		this.mockDataForAccountProvisioning(0);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createOrUpdateNewAccountProvisioningPlanExternalRepo(spc,
				APPLICATION_NAME, newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() > 0);
		assertEquals("The operation name do not match", AccountRequest.Operation.Create,
				allRequests.get(0).getOperation());
	}
	/**
	 * Test method for createOrUpdateNewAccountProvisioningPlanExternalRepo()
	 * 
	 * Test condition: No links associated to identity service
	 * 
	 * Expected Results: Empty account list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateOrUpdateNewAccountProvisioningPlanExternalRepo_InvalidNativeIdNoLinks() throws Exception {
		this.mockDataForAccountProvisioning(1);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createOrUpdateNewAccountProvisioningPlanExternalRepo(spc,
				APPLICATION_NAME, newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() == 0);
	}
	/**
	 * Test method for createOrUpdateNewAccountProvisioningPlanExternalRepo()
	 * 
	 * Test condition: No field value associated to identity forms
	 * 
	 * Expected Results: Empty account list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateOrUpdateNewAccountProvisioningPlanExternalRepo_NoValueProvisioningForm() throws Exception {
		this.mockDataForAccountProvisioning(1);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createOrUpdateNewAccountProvisioningPlanExternalRepo(spc,
				APPLICATION_NAME, newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() == 0);
	}
	/**
	 * Test method for createOrUpdateNewAccountProvisioningPlanExternalRepo()
	 * 
	 * Test condition: Field value associated to identity form but with changed
	 * attribute value
	 * 
	 * Expected Results: Empty account list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateOrUpdateNewAccountProvisioningPlanExternalRepo_ValidProvisionFormValueWithChangedAttributeValue()
			throws Exception {
		this.mockDataForAccountProvisioning(1);
		Mockito.when(ROADUtil.getFieldValueFromProvisioningForms(Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.any(),Mockito.any())).thenReturn(ATT_VALUE_PP_FORM);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createOrUpdateNewAccountProvisioningPlanExternalRepo(spc,
				APPLICATION_NAME, newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() == 0);
	}
	/**
	 * Test method for createOrUpdateNewAccountProvisioningPlanExternalRepo()
	 * 
	 * Test condition: Field value associated to identity form but with changed
	 * attribute value
	 * 
	 * Expected Results: Empty account list is returned
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateOrUpdateNewAccountProvisioningPlanExternalRepo_ValidProvisionFormValueWithUnChangedAttributeValue()
			throws Exception {
		this.mockDataForAccountProvisioning(1);
		PowerMockito.mockStatic(AttributeSyncRuleLibrary.class);
		Mockito.when(AttributeSyncRuleLibrary.isAttributeValueChanged(spc, newIdentity, null, ATT_DEF__NAME,
				ATT_DEF__NAME, IDENTITY_NAME, ATT_VALUE_PP_FORM, ATT_VALUE_PP_FORM, true)).thenReturn(true);
		Mockito.when(ROADUtil.getFieldValueFromProvisioningForms(Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.any(),Mockito.any())).thenReturn(ATT_VALUE_PP_FORM);
		List<AccountRequest> allRequests = JoinerRuleLibrary.createOrUpdateNewAccountProvisioningPlanExternalRepo(spc,
				APPLICATION_NAME, newIdentity);
		assertTrue("The provision plan is not empty", allRequests.size() > 0);
		assertEquals("Operation name mismatch", AccountRequest.Operation.Modify, allRequests.get(0).getOperation());
		assertEquals("Operation name mismatch", APPLICATION_NAME, allRequests.get(0).getApplicationName());
	}
	/**
	 * Helper method to mock data for account provisioning
	 * 
	 * @param linkCount
	 * @throws Exception
	 */
	private void mockDataForAccountProvisioning(int linkCount) throws Exception {
		Identity identity = this.getIdentity(true);
		List<Link> linkList = new ArrayList<Link>();
		Link link = new Link();
		link.setNativeIdentity(IDENTITY_NAME);
		linkList.add(link);
		link.setAttribute(ATT_DEF__NAME, ATT_VALUE_PP_FORM);
		newIdentity.setName(IDENTITY_NAME);
		PowerMockito.mockStatic(ROADUtil.class);
		Mockito.when(ROADUtil.getNativeIdentity(spc, APPLICATION_NAME, newIdentity)).thenReturn("1");
		Mockito.when(spc.getObjectByName(Identity.class, IDENTITY_NAME)).thenReturn(identity);
		IdentityService identityService = Mockito.mock(IdentityService.class);
		Mockito.when(identityService.countLinks(newIdentity, this.getApplication(true))).thenReturn(linkCount);
		Mockito.when(spc.getObjectByName(Application.class, APPLICATION_NAME)).thenReturn(this.getApplication(true));
		Mockito.when(identityService.getLinks(newIdentity, this.getApplication(true))).thenReturn(linkList);
		PowerMockito.whenNew(IdentityService.class).withArguments(spc).thenReturn(identityService);
	}
	/**
	 * Helper method to mock Application
	 * 
	 * @return
	 */
	private Application getApplication(boolean addJoinerPop) {
		Application application = new Application();
		AttributeDefinition attrDef = new AttributeDefinition();
		application.setAttribute(JOINERROLES, JOINERROLES_VALUE);
		if (addJoinerPop) {
			application.setAttribute(JOINERPOPREGEX, JOINERTOKEN);
		}
		attrDef.setName(ATT_DEF__NAME);
		Schema schema = new Schema();
		schema.setObjectType(Schema.TYPE_ACCOUNT);
		schema.addAttributeDefinition(attrDef);
		application.setSchema(schema);
		application.setName(APPLICATION_NAME);
		return application;
	}
	/**
	 * Helper method to mock the iterator results
	 * 
	 * @throws Exception
	 */
	private void mockIterator() throws Exception {
		Object[] objectArr = { BUNDLE_NAME };
		Iterator iterator = Mockito.mock(Iterator.class);
		Mockito.when(iterator.hasNext()).thenReturn(true, false);
		Mockito.when(iterator.next()).thenReturn(objectArr);
		Mockito.when(spc.search(Mockito.any(), Mockito.any(QueryOptions.class), Mockito.anyList()))
				.thenReturn(iterator);
	}
	/**
	 * Helper method to get an Identity
	 * 
	 * @return
	 */
	private Identity getIdentity(boolean addExistingRole) {
		Identity identity = new Identity();
		identity.setName(IDENTITY_NAME);
		identity.setDetectedRoles(this.getBundleList(addExistingRole));
		return identity;
	}
	/**
	 * Helper method to get BundleList
	 * 
	 * @return
	 */
	private List<Bundle> getBundleList(boolean addExistingRole) {
		List<Bundle> bundleList = new ArrayList<Bundle>();
		if (addExistingRole) {
			Bundle bundle = new Bundle();
			bundle.setName(BUNDLE_NAME);
			bundleList.add(bundle);
		}
		return bundleList;
	}
	/**
	 * Helper method to get all the Relationship Changes
	 * 
	 * @return
	 */
	private List<String> getRelationShipChanges() {
		List<String> relationshipList = new ArrayList<String>();
		relationshipList.add(WrapperRuleLibrary.PERSONAADD);
		return relationshipList;
	}
}
