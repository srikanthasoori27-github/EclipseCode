/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.Brand;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
/**
 * 
 * Test class for {@link LeaverRuleLibrary}
 * @author sachit.khanna
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({BrandingServiceFactory.class, BrandingService.class, WrapperRuleLibrary.class, LeaverRuleLibrary.class})
public class LeaverRuleLibraryTest {
	private SailPointContext mockSailPointContext;
	private Identity mockNewIdentity;
	private Identity mockPreviousIdentity;
	private BrandingService mockBrandingService;
	/**
	 * Creates mock objects for all dependencies outside the test class.
	 */
	@Before
	public void setUp() {
		mockStatic(BrandingServiceFactory.class);
		mockBrandingService = mock(BrandingService.class);
		when(mockBrandingService.getAdminUserName()).thenReturn("Sailpoint");
		when(mockBrandingService.getBrand()).thenReturn(Brand.IIQ);
		when(BrandingServiceFactory.getService()).thenReturn(mockBrandingService);
		mockSailPointContext = mock(SailPointContext.class);
		mockNewIdentity = mock(Identity.class);
		mockPreviousIdentity = mock(Identity.class);
		mockStatic(WrapperRuleLibrary.class);
		spy(LeaverRuleLibrary.class);
	}
	/**
	 * Performs cleanup on the mocked dependencies.
	 * 
	 * @throws Exception on encountering an unexpected error
	 */
	@After
	public void tearDown() throws Exception {
		mockBrandingService = null;		
		mockSailPointContext = null;
		mockNewIdentity = null;
		mockPreviousIdentity = null;
	}	
	/**
	 * Mocks {@link WrapperRuleLibrary} to make validateTriggersBeforePersona return false
	 * and validates that  {@link LeaverRuleLibrary#isEligibleForLeaver(SailPointContext, Identity, Identity)} returns false.
	 * @throws Exception on encountering an unexpected error
	 */
	@Test
	public void testIsEligibleForLeaver_PersonaEnabledAndTriggersBeforePersonaInvalid() throws Exception {
		when(WrapperRuleLibrary.isPersonaEnabled(mockSailPointContext)).thenReturn("TRUE");
		when(WrapperRuleLibrary.validateTriggersBeforePersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity, LeaverRuleLibrary.LEAVERPROCESS))
			.thenReturn(Boolean.FALSE);
		assertFalse(LeaverRuleLibrary.isEligibleForLeaver(mockSailPointContext, mockPreviousIdentity, mockNewIdentity));
	}
	/**
	 * Mocks {@link WrapperRuleLibrary} to make checkIsNewRelationshipPersona return true
	 * and validates that  {@link LeaverRuleLibrary#isEligibleForLeaver(SailPointContext, Identity, Identity)} returns false.
	 * @throws Exception on encountering an unexpected error
	 */
	@Test
	public void testIsEligibleForLeaver_PersonaEnabledAndIsNewRelationshipPersona() throws Exception {
		when(WrapperRuleLibrary.isPersonaEnabled(mockSailPointContext)).thenReturn("TRUE");
		when(WrapperRuleLibrary.validateTriggersBeforePersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity, LeaverRuleLibrary.LEAVERPROCESS))
			.thenReturn(Boolean.TRUE);
		when(WrapperRuleLibrary.checkIsNewRelationshipPersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity))
			.thenReturn(Boolean.TRUE);
		assertFalse(LeaverRuleLibrary.isEligibleForLeaver(mockSailPointContext, mockPreviousIdentity, mockNewIdentity));		
	}
	/**
	 * Mocks {@link WrapperRuleLibrary} to make isTerminationPersona return true
	 * and validates that  {@link LeaverRuleLibrary#isEligibleForLeaver(SailPointContext, Identity, Identity)} returns true.
	 * @throws Exception on encountering an unexpected error
	 */
	@Test
	public void testIsEligibleForLeaver_PersonaEnabledAndIsTerminationPersona() throws Exception {
		when(WrapperRuleLibrary.isPersonaEnabled(mockSailPointContext)).thenReturn("TRUE");
		when(WrapperRuleLibrary.validateTriggersBeforePersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity, LeaverRuleLibrary.LEAVERPROCESS))
			.thenReturn(Boolean.TRUE);
		when(WrapperRuleLibrary.checkIsNewRelationshipPersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity))
			.thenReturn(Boolean.FALSE);
		when(WrapperRuleLibrary.checkIsTerminationPersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity))
			.thenReturn(Boolean.TRUE);
		assertTrue(LeaverRuleLibrary.isEligibleForLeaver(mockSailPointContext, mockPreviousIdentity, mockNewIdentity));
	}
	/**
	 * Mocks {@link WrapperRuleLibrary} to make Leaver Pre-Requisites Invalid
	 * and validates that  {@link LeaverRuleLibrary#isEligibleForLeaver(SailPointContext, Identity, Identity)} returns false.
	 * @throws Exception on encountering an unexpected error
	 */
	@Test
	public void testIsEligibleForLeaver_PersonaEnabledAndOtherPreRequisitesNotMet() throws Exception {
		when(WrapperRuleLibrary.isPersonaEnabled(mockSailPointContext)).thenReturn("TRUE");
		when(WrapperRuleLibrary.validateTriggersBeforePersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity, LeaverRuleLibrary.LEAVERPROCESS))
			.thenReturn(Boolean.TRUE);
		when(WrapperRuleLibrary.checkIsNewRelationshipPersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity))
			.thenReturn(Boolean.FALSE);
		when(WrapperRuleLibrary.checkIsTerminationPersona(
				mockSailPointContext, mockNewIdentity, mockPreviousIdentity))
			.thenReturn(Boolean.FALSE);
		assertFalse(LeaverRuleLibrary.isEligibleForLeaver(mockSailPointContext, mockPreviousIdentity, mockNewIdentity));		
	}
}
