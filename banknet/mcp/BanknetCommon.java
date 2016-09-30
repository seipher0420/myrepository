/*
 * Copyright (c) 2008-2010 AEON Credit Technology Systems, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of AEON
 * Credit Technology Systems, Phil. Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with AEON Credit Technology.
 *
 * AEON MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING
 * 
 * DATE       AUTHOR          REMARKS / COMMENT
 * ====-------======----------=================------------------------------
 * 
 * Apr-22-10  JLiwanag       Initial
 * Jun-01-10  LQuirona       Update
 */

package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

/**
 * Execute the common process of Banknet.
 * 
 * @author jliwanag
 */
import java.util.Properties;

import org.apache.log4j.Level;
import org.jpos.iso.packager.GenericValidatingPackager;

import com.aeoncredit.fep.core.adapter.brand.common.CommonBrandMCP;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.adapter.brand.common.Keys;

import static com.aeoncredit.fep.core.adapter.brand.common.Keys.XMLTags.*;
import com.aeoncredit.fep.framework.base.AeonProcessImpl;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;

/**
 * Execute the common process of Banknet.
 * @author eregodon
 */
public class BanknetCommon extends CommonBrandMCP {
	/** Acquiring Institution Id Code */
	private String acquiringInstitutionIdCode;
	/** Acquiring Institution Country Code */
	private String acquiringInstitutionCountryCode;
	/** Currency Code Transaction */
	private String currencyCodeTransaction;
	/** Forwarding Institution Id Code */
	private String forwardingInstitutionIdCode;
	/** Authorization Control Timer */
	private Number authorizationControlTimer;
	/** Authorization Timer */
	private Number authorizationTimer;
	/** Control System Timer */
	private Number controlSystemTimer;
	/** MTI_0400 Resend Count */
	private Number resendCount;
	/** Application Generation Number */
	private Number applicationGenerationNumber;
	/** Acquiring Encryption Information - Encryption Mode */
	private String acquiringEncryptionInformationEncryptionMode;
	/** Acquiring Encryption Information - Key Name */
	private String acquiringEncryptionInformationKeyName;
	/** Issuing Encryption Information - Encryption Mode */
	private String issuingEncryptionInformationEncryptionMode;
	/** Issuing Encryption Information - Key Name */
	private String issuingEncryptionInformation_keyName;
	/** Service Indicator */
	private String serviceIndicator;
	/** Package Configuration File */
	private String packageConfigurationFile;
	/** Format Check File */
	private String formatCheckFile;
	/** No Sign-ON Response Acquiring Send Flag */
	private String noSignOnResponseAcquiringSendFlag;
	/** MasterCard Customer ID Number Information*/
	private String masterCardCustIdNoInfo;
	/** HSM Config File */
	private String hsmConfigFile;
	/** IC Tag Configuration File */
	private String icTagConfigurationFile;
	/** TPK-PIN-Block-Format-Code */
	private String tpkPinBlockFormatCode;
    /** ZPK-PIN-Block-Format-Code */
    private String zpkPinBlockFormatCode;
	/** Negative Acknowledgment send to Host SAF Flag */
	private String negativeAckSendToHostFlag;
	/** Unsupported Transaction Type Code for HK */
	private String supportedTransactionType;
	/**Partial Reversal Indicator */
	private String partialReversalIndicator;
	/** csawi 20111006:Magnetic Stripe Compliance Check */
	private String magneticStripeComplianceCheck;
	/** [mqueja: 11/22/2011] [added message security code tag] */
	private String messageSecurityCode;
	/** [salvaro:11/28/2011] [Added Magnetic Stripe Compliance Check Flag] */
	private String  magneticStripeComplianceCheckFlag;
	/** [salvaro:11/28/2011] [Added Chip Obs Validicty Check Flag] */
	private String  chipObsValidictyCheckFlag;
	/** [mqueja:04/12/2012] [Added Checking of Currency Code] */
	private String currencyCodeDecimalCheck;
	private Properties properties;
	/** [mqueja:05/23/2012] [Redmine#3873] [Send_Timeout_Response_Flag] */
	private String sendTimeoutResponseFlag;
	/** Variables for Exception Handling */
	/** 04222015 ACSS)MLim Sending to Master Card if Standin Handling */
	private String sendStandinResponseFlag;
	String[] args = new String[2];
	String sysMsgId;
	
    /**
     * Initialize the member value for Banknet
     * @param mcpProcess The instance of AeonProcessImpl
     */
	public BanknetCommon(IMCPProcess mcpProcess) {
		super(mcpProcess);
		init();
	}

	/**
	 * Initializes all variables for Banknet.
	 * @author jliwanag
	 * @since 1.0
	 */
	public void init() {
		try { 
			this.acquiringInstitutionIdCode = (String)mcpProcess.getAppParameter(AQ_INSTITUTION_ID_CODE);
			this.acquiringInstitutionCountryCode = (String)mcpProcess.getAppParameter(AQ_INSTITUTION_COUNTRY_CODE);
			this.currencyCodeTransaction = (String)mcpProcess.getAppParameter(CURRENCY_CODE_TRANSACTION);
			this.forwardingInstitutionIdCode = (String) mcpProcess.getAppParameter(FW_INSTITUTION_ID_CODE);
			this.authorizationControlTimer = Integer.parseInt((String)mcpProcess.getAppParameter(AUTHORIZATION_CONTROL_TIMER));
			this.authorizationTimer = Integer.parseInt((String)mcpProcess.getAppParameter(AUTHORIZATION_TIMER));
			this.controlSystemTimer = Integer.parseInt((String)mcpProcess.getAppParameter(CONTROL_SYSTEM_TIMER));
			this.resendCount = Integer.parseInt((String)mcpProcess.getAppParameter(MTI0400_RESEND_COUNT));
			this.applicationGenerationNumber = (Number)mcpProcess.getAppParameter(APPLICATION_GENERATION_NUMBER);
			this.acquiringEncryptionInformationEncryptionMode = (String) mcpProcess.getAppParameter(AQ_ENCRYPTION_MODE);
			this.acquiringEncryptionInformationKeyName = (String) mcpProcess.getAppParameter(AQ_KEY_NAME);
			this.issuingEncryptionInformationEncryptionMode = (String) mcpProcess.getAppParameter(IS_ENCRYPTION_MODE);
			this.issuingEncryptionInformation_keyName = (String) mcpProcess.getAppParameter(IS_KEY_NAME);
			this.serviceIndicator = (String) mcpProcess.getAppParameter(SERVICE_INDICATOR);
			this.packageConfigurationFile = (String) mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE);
			this.formatCheckFile = (String) mcpProcess.getAppParameter(FORMAT_CHECK_FILE);
			this.noSignOnResponseAcquiringSendFlag = 
				(String) mcpProcess.getAppParameter(NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG); 
			this.hsmConfigFile = (String) mcpProcess.getAppParameter(HSM_CONFIG_FILE);
			this.masterCardCustIdNoInfo = (String) mcpProcess.getAppParameter(MASTERCARD_CUSTOMER_ID_NUMBER);
			this.icTagConfigurationFile = (String) mcpProcess.getAppParameter(IC_TAG_CONFIGURATION_FILE);
			this.packager = new GenericValidatingPackager(this.packageConfigurationFile);
			// [mquines:09/13/2010] [Redmine#1047] [MasterCard Customer ID Number variable-length is 11.]
			// [lsosuan:01/03/2011] [Redmine#2116] [[MDS-Acquiring] HSM Error code 24]
			this.tpkPinBlockFormatCode = FepUtilities.getTPKFormat((String)mcpProcess.getAppParameter(
					FEPMCP_CONFIG_FILE_INFORMATION));
			// [emaranan:02/12/2011] Add ZPK value from FepMCP.cfg.xml
            this.zpkPinBlockFormatCode = FepUtilities.getZPKFormat((String)mcpProcess.getAppParameter(
                            FEPMCP_CONFIG_FILE_INFORMATION));
			// [lquirona:07/11/2011] [Redmine#3106] [0190 Send to HOST SAF configurable]
			this.negativeAckSendToHostFlag = (String)mcpProcess.getAppParameter(NEGATIVE_ACK_SEND_TO_HOST_FLAG);
			this.supportedTransactionType = (String)mcpProcess.getAppParameter(SUPPORTED_TRANSACTION_TYPE);
			this.partialReversalIndicator = (String)mcpProcess.getAppParameter(PARTIAL_REVERSAL_INDICATOR);
			// [salvaro:11/28/2011] [Redmine#3405] [Added Flags for CHIP_OBS_VALIDICTY_CHECK_FLAG and 
			this.chipObsValidictyCheckFlag = (String)mcpProcess.getAppParameter(CHIP_OBS_VALIDICTY_CHECK_FLAG);
			this.magneticStripeComplianceCheckFlag = (String)mcpProcess.getAppParameter(MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG);
			
			// csawi 20111006: included magnetic stripe compliance check
			this.magneticStripeComplianceCheck = (String)mcpProcess.getAppParameter(MAGNETIC_STRIPE_COMPLIANCE_CHECK);
			// [mqueja:11/22/2011] [added message security code]
			this.messageSecurityCode = (String)mcpProcess.getAppParameter(MESSAGE_SECURITY_CODE);
			// [mqueja:04/12/2012] [Redmine#3832] [Added Checking of Currency Code]
			this.properties	= (Properties) mcpProcess.getAppParameter(CURRENCRY_CODE_DECIMAL_CHECK);
			this.sendTimeoutResponseFlag	= (String) mcpProcess.getAppParameter(Keys.XMLTags.SEND_TIMEOUT_RESPONSE_FLAG);
			this.sendStandinResponseFlag	= (String) mcpProcess.getAppParameter(Keys.XMLTags.SEND_STANDIN_RESPONSE_FLAG);
			//mcpProcess.writeAppLog(Level.DEBUG, "SEND_STANDIN_RESPONSE_FLAG : " + sendStandinResponseFlag);
			
		} catch (Exception e) {
			args[0] = e.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, Constants.SERVER_SYSLOG_INFO_2027);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, Constants.SERVER_SYSLOG_INFO_2027, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommon] init(): End Process");
			return;
		}
	}// End of init()

	/**
	 * @return IMCPProcess <code>AeonProcessImpl</code> instance
	 * @see AeonProcessImpl
	 */
	protected IMCPProcess getIMCPProcess() {
		return this.mcpProcess;
	}

	/**
	 * @return String Acquiring Institution ID Code
	 */
	protected String getAcquiringInstitutionIdCode() {
		return acquiringInstitutionIdCode;
	}

	/**
	 * @return String Acquiring Institution Country Code
	 */
	protected String getAcquiringInstitutionCountryCode() {
		return acquiringInstitutionCountryCode;
	}

	/**
	 * @return String Currency Transaction Code
	 */
	protected String getCurrencyCodeTransaction() {
		return currencyCodeTransaction;
	}

	/**
	 * @return String Forwarding Institution ID Code
	 */
	protected String getForwardingInstitutionIdCode() {
		return forwardingInstitutionIdCode;
	}

	/**
	 * @return String Authorization Control Timer
	 */
	protected Number getAuthorizationControlTimer() {
		return authorizationControlTimer;
	}

	/**
	 * @return String Authorization Timer
	 */
	protected Number getAuthorizationTimer() {
		return authorizationTimer;
	}

	/**
	 * @return String Control System Timer
	 */
	protected Number getControlSystemTimer() {
		return controlSystemTimer;
	}

	/**
	 * @return String 0400 Resend Count
	 */
	protected Number get0400ResendCount() {
		return resendCount;
	}

	/**
	 * @return String Application Generation Number
	 */
	protected Number getApplicationGenerationNumber() {
		return applicationGenerationNumber;
	}

	/**
	 * @return String Acquiring Encryption Information - Encryption Mode
	 */
	protected String getAcquiringEncryptionInformationEncryptionMode() {
		return acquiringEncryptionInformationEncryptionMode;
	}

	/**
	 * @return String Acquiring Encryption Information - Key Name
	 */
	protected String getAcquiringEncryptionInformationKeyName() {
		return acquiringEncryptionInformationKeyName;
	}

	/**
	 * @return String Issuing Encryption Information - Encryption Mode
	 */
	protected String getIssuingEncryptionInformationEncryptionMode() {
		return issuingEncryptionInformationEncryptionMode;
	}

	/**
	 * @return String Issuing Encryption Information - Key Name
	 */
	protected String getIssuingEncryptionInformationKeyName() {
		return issuingEncryptionInformation_keyName;
	}

	/**
	 * @return String Service Indicator
	 */
	protected String getServiceIndicator() {
		return serviceIndicator;
	}

	/**
	 * @return String Package Configuration File
	 */
	protected String getPackageConfigurationFile() {
		return packageConfigurationFile;
	}

	/**
	 * @return String Format Check File
	 */
	protected String getFormatCheckFile() {
		return formatCheckFile;
	}

	/**
	 * @return String No Sign-ON Response Acquiring Send Flag
	 */
	protected String getNoSignOnResponseAcquiringSendFlag(){
		return noSignOnResponseAcquiringSendFlag;
	}

	/**
	 * @return String MasterCard Customer ID Number Information
	 */
	protected String getMasterCardCustIdNoInfo() {
		return masterCardCustIdNoInfo;
	}

	/**
	 * @return String HSM Configuration File
	 */
	protected String gethsmConfigFile() {
		return hsmConfigFile;
	}

	/**
	 * @return String IC Tag Configuration File
	 */
	protected String geticTagConfigurationFile() {
		return icTagConfigurationFile;
	}

	/**
	 * @return String TPK Pin block format
	 */
	public String getTpkPinBlockFormatCode() {
		return tpkPinBlockFormatCode;
	}
    
    /**
     * @return String ZPK Pin block format
     */
    public String getZpkPinBlockFormatCode() {
        return zpkPinBlockFormatCode;
    }

	/**
	 * @return String NegativeAckSendToHostFlag
	 */
	public String getNegativeAckSendToHostFlag() {
		return negativeAckSendToHostFlag;
	}

	/**
	 * @return String Partial Reversal Indicator From Config File
	 */
	public String getPartialReversalIndicator() {
		return partialReversalIndicator;
	}

	/**
	 * @return String Supported Transaction from Config File
	 */
	public String getSupportedTransactionType() {
		return supportedTransactionType;
	}
	
	/* csawi 20111006: included the following getters in reference
	 * with the magnetic stripe compliance check, and getting of track2
	 * and track1.
	 */
	/**
	 * @return String magneticStripeComplianceCheck
	 */
	public String getMagneticStripeComplianceCheck() {
		return magneticStripeComplianceCheck;
	}

	/**
	 * @return String Message Security Code
	 */
	public String getMessageSecurityCode() {
		return messageSecurityCode;
	}
	
	/**
	 * [salvaro:11/28/2011]
	 * @return String Chip Obs Validicty Check Flag
	 */
	public String getChipObsValidictyCheckFlag() {
		return chipObsValidictyCheckFlag;
	}

	/**
	 * [salvaro:11/28/2011] 
	 * @return String Magnetic Stripe Compliance Check Flag
	 */
	public String getMagneticStripeComplianceCheckFlag() {
		return magneticStripeComplianceCheckFlag;
	}
	
	/**
	 * [mqueja:4/12/2012] 
	 * @return String Currency Code Decimal Check Properties
	 */
	public String getCurrencyCodeDecimalCheck() {
		return currencyCodeDecimalCheck;
	}

	public Properties getProperties() {
		return properties;
	}
	
	public String getSendTimeoutResponseFlag() {
		return sendTimeoutResponseFlag;
	}

	/**
	 * 04222015 ACSS)MLim
	 * @return sendStandInResponseFlag Value from configuration.
	 */
	public String getSendStandinResponseFlag() {
		return sendStandinResponseFlag;
	}
}// End of class
