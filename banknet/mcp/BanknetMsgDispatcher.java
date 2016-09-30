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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;
import org.w3c.dom.Element;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.AbstractMessageDispatcher;
import com.aeoncredit.fep.core.adapter.brand.common.CommonBrandCommand;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.adapter.brand.common.Keys;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.framework.base.ProcessParameters;
import com.aeoncredit.fep.framework.exception.AeonDBException;
import com.aeoncredit.fep.framework.exception.AeonException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.exception.XMLParseException;
import com.aeoncredit.fep.framework.hsmaccess.HSMThalesAccess;
import com.aeoncredit.fep.framework.hsmaccess.HSMThalesAccess.HSMUser;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.msgcommu.QueueConfigMap;
import com.aeoncredit.fep.framework.util.XMLUtil;

import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;
import static com.aeoncredit.fep.core.adapter.brand.common.Keys.XMLTags.*;

/**
 * Decode the message and dispatch the message received from queues of MCP to
 * its process according to queue header and MTI.
 */
public class BanknetMsgDispatcher extends AbstractMessageDispatcher {

	/** Banknet Issuing Request MTI values */
	private final List<String> BNK_IS_REQUEST_MTI_LIST = Arrays.asList(
			SysCode.MTI_0100, 
			SysCode.MTI_0120, 
			SysCode.MTI_0400,
			SysCode.MTI_0420, 
			SysCode.MTI_0190);

	/** Banknet Issuing Response MTI values */
	private final List<String> BNK_IS_RESPONSE_MTI_LIST = Arrays.asList(
			SysCode.MTI_0100, 
			SysCode.MTI_0400);

	/** new BanknetAcRequest(mcpProcess) Transaction Code List */
	private final List<String> BNK_AC_REQUEST_TRANS_CODE_PREFIX_LIST = Arrays.asList(
			PREFIX_TRANS_CODE_AUTHORIZATION_REQ_1,
			PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1,
			PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE,
			PREFIX_TRANS_CODE_AUTHORIZATION_REQ_4);

	/** Banknet Acquiring Response MTI values */
	private final List<String> BNK_AC_RESPONSE_MTI_LIST = Arrays.asList(
			SysCode.MTI_0110, 
			SysCode.MTI_0410);

	/** Banknet Command MTI values */
	private final List<String> BNK_COMMAND_MTI_LIST = Arrays.asList(
			SysCode.MTI_0800, 
			SysCode.MTI_0620, 
			SysCode.MTI_0810,
			SysCode.MTI_0820, 
			SysCode.MTI_0312);

	/** Banknet Command PTI values */
	private final List<String> BNK_COMMAND_PROCESSING_TYPE_IDENTIFIER = Arrays.asList(
			SysCode.QH_PTI_PREFIX_SIGN_ON,
			SysCode.QH_PTI_PREFIX_SIGN_OFF, 
			SysCode.QH_PTI_SIGN_ON,
			SysCode.QH_PTI_SIGN_OFF, 
			SysCode.QH_PTI_ECHO_TEST,
			SysCode.QH_PTI_ADVICE_MESSAGE);
	
	private Properties properties = new Properties();

	/** Variables for Exception Handling */
	String[] args = new String[2];
	String sysMsgId;

	/**
	 * Read the configuration file. Store the values from Configuration file and
	 * LCP location in a HashMap. This method also sets the packager to be used
	 * for Banknet message processing.
	 */
	@Override
	public void initialize() {
		XMLUtil xmlUtil;
		ProcessParameters param;
		Element nodeElement;

		/*
		 * Read the contents in the configuration file to save them in the
		 * memory, and they will be used by BanknetCommon class Create a private
		 * HashMap to store values from XML Configutation and SysCode for LCP
		 * Location. Set the packager.
		 */
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "[BanknetMsgDispatcher] initialize() start");

		xmlUtil = XMLUtil.getInstance();
		param = ProcessParameters.getInstance();
		nodeElement = param.getNodeElement();

		try {
			// Get and store the values from MCP Configuration File
			mcpProcess.putAppParameter(AQ_INSTITUTION_ID_CODE, xmlUtil.getContent(nodeElement, AQ_INSTITUTION_ID_CODE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + AQ_INSTITUTION_ID_CODE.getString() 
					+ ": " + parameters.getAppParam(AQ_INSTITUTION_ID_CODE));

			mcpProcess.putAppParameter(AQ_INSTITUTION_COUNTRY_CODE, 
					xmlUtil.getContent(nodeElement, AQ_INSTITUTION_COUNTRY_CODE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + AQ_INSTITUTION_COUNTRY_CODE.getString() 
					+ ": " + parameters.getAppParam(AQ_INSTITUTION_COUNTRY_CODE));

			mcpProcess.putAppParameter(CURRENCY_CODE_TRANSACTION, 
					xmlUtil .getContent(nodeElement, CURRENCY_CODE_TRANSACTION .getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + CURRENCY_CODE_TRANSACTION.getString() + 
					": " + parameters.getAppParam(CURRENCY_CODE_TRANSACTION));

			mcpProcess.putAppParameter(FW_INSTITUTION_ID_CODE, 
					xmlUtil.getContent(nodeElement, FW_INSTITUTION_ID_CODE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + FW_INSTITUTION_ID_CODE.getString() +
					": " + parameters.getAppParam(FW_INSTITUTION_ID_CODE));

			mcpProcess.putAppParameter(AUTHORIZATION_CONTROL_TIMER, 
					(xmlUtil.getContent(nodeElement, AUTHORIZATION_CONTROL_TIMER.getString())));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + AUTHORIZATION_CONTROL_TIMER.getString() 
					+ ": " + parameters.getAppParam(AUTHORIZATION_CONTROL_TIMER));

			mcpProcess.putAppParameter(AUTHORIZATION_TIMER, 
					(xmlUtil.getContent(nodeElement, AUTHORIZATION_TIMER.getString())));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + AUTHORIZATION_TIMER.getString() 
					+ ": " + parameters.getAppParam(AUTHORIZATION_TIMER));

			mcpProcess.putAppParameter(CONTROL_SYSTEM_TIMER, 
					(xmlUtil.getContent(nodeElement, CONTROL_SYSTEM_TIMER.getString())));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + CONTROL_SYSTEM_TIMER.getString() 
					+ ": " + parameters.getAppParam(CONTROL_SYSTEM_TIMER));

			mcpProcess.putAppParameter(MTI0400_RESEND_COUNT, 
					(xmlUtil.getContent(nodeElement, MTI0400_RESEND_COUNT.getString())));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + MTI0400_RESEND_COUNT.getString() 
					+ ": " + parameters.getAppParam(MTI0400_RESEND_COUNT));

			mcpProcess.putAppParameter(APPLICATION_GENERATION_NUMBER, 
					Integer.parseInt(xmlUtil.getContent(nodeElement, APPLICATION_GENERATION_NUMBER.getString())));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + APPLICATION_GENERATION_NUMBER.getString() 
					+ ": " + parameters.getAppParam(APPLICATION_GENERATION_NUMBER));

			mcpProcess.putAppParameter(AQ_ENCRYPTION_MODE, 
					xmlUtil.getContent(xmlUtil.getElementsByTag(nodeElement,
					Keys.XMLTags.ACQUIRING_ENCRYPTION_INFORMATION.getString())[0], AQ_ENCRYPTION_MODE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + AQ_ENCRYPTION_MODE.getString() 
					+ ": " + parameters.getAppParam(AQ_ENCRYPTION_MODE));

			mcpProcess.putAppParameter(AQ_KEY_NAME, 
					xmlUtil.getContent(xmlUtil.getElementsByTag(nodeElement,
					Keys.XMLTags.ACQUIRING_ENCRYPTION_INFORMATION.getString())[0], AQ_KEY_NAME.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + AQ_KEY_NAME.getString() 
					+ ": " + parameters.getAppParam(AQ_KEY_NAME));

			mcpProcess.putAppParameter(IS_ENCRYPTION_MODE, 
					xmlUtil.getContent(xmlUtil.getElementsByTag(nodeElement,
					Keys.XMLTags.ISSUING_ENCRYPTION_INFORMATION.getString())[0], IS_ENCRYPTION_MODE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + IS_ENCRYPTION_MODE.getString() 
					+ ": " + parameters.getAppParam(IS_ENCRYPTION_MODE));

			mcpProcess.putAppParameter(IS_KEY_NAME, 
					xmlUtil.getContent(xmlUtil.getElementsByTag(nodeElement,
					Keys.XMLTags.ISSUING_ENCRYPTION_INFORMATION.getString())[0], IS_KEY_NAME.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + IS_KEY_NAME.getString() 
					+ ": " + parameters.getAppParam(IS_KEY_NAME));

			mcpProcess.putAppParameter(SERVICE_INDICATOR, 
					xmlUtil.getContent(nodeElement, SERVICE_INDICATOR.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + SERVICE_INDICATOR.getString() 
					+ ": " + parameters.getAppParam(SERVICE_INDICATOR));

			mcpProcess.putAppParameter(PACKAGE_CONFIGURATION_FILE, 
					xmlUtil.getContent(nodeElement, PACKAGE_CONFIGURATION_FILE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + PACKAGE_CONFIGURATION_FILE.getString()
					+ ": " + parameters.getAppParam(PACKAGE_CONFIGURATION_FILE));

			mcpProcess.putAppParameter(FORMAT_CHECK_FILE, 
					xmlUtil.getContent(nodeElement, FORMAT_CHECK_FILE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " + FORMAT_CHECK_FILE.getString() + ": "
					+ parameters.getAppParam(FORMAT_CHECK_FILE));

			mcpProcess.putAppParameter(Keys.XMLTags.NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG,
					xmlUtil.getContent(nodeElement, Keys.XMLTags.NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
					"[BanknetMsgDispatcher] Loading: " 
					+ Keys.XMLTags.NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG));

			// [Megan Quines: 09/10/2010] Redmine ssue# 1047
			mcpProcess.putAppParameter(Keys.XMLTags.MASTERCARD_CUSTOMER_ID_NUMBER, 
					xmlUtil.getContent(nodeElement, Keys.XMLTags.MASTERCARD_CUSTOMER_ID_NUMBER.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.MASTERCARD_CUSTOMER_ID_NUMBER.getString()
					+ ": " + parameters.getAppParam(Keys.XMLTags.MASTERCARD_CUSTOMER_ID_NUMBER));

			mcpProcess.putAppParameter(Keys.XMLTags.IC_TAG_CONFIGURATION_FILE, 
					xmlUtil.getContent(nodeElement, IC_TAG_CONFIGURATION_FILE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.IC_TAG_CONFIGURATION_FILE.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.IC_TAG_CONFIGURATION_FILE));

			mcpProcess.putAppParameter(Keys.XMLTags.NEGATIVE_ACK_SEND_TO_HOST_FLAG, 
					xmlUtil.getContent(nodeElement,
					NEGATIVE_ACK_SEND_TO_HOST_FLAG.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.NEGATIVE_ACK_SEND_TO_HOST_FLAG.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.NEGATIVE_ACK_SEND_TO_HOST_FLAG));

			mcpProcess.putAppParameter(Keys.XMLTags.SUPPORTED_TRANSACTION_TYPE,
					xmlUtil.getContent(nodeElement, SUPPORTED_TRANSACTION_TYPE.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.SUPPORTED_TRANSACTION_TYPE.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.SUPPORTED_TRANSACTION_TYPE));

			mcpProcess.putAppParameter(Keys.XMLTags.PARTIAL_REVERSAL_INDICATOR, 
					xmlUtil.getContent(nodeElement,PARTIAL_REVERSAL_INDICATOR.getString()));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.PARTIAL_REVERSAL_INDICATOR.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.PARTIAL_REVERSAL_INDICATOR));
			
			mcpProcess.putAppParameter(LCP_LOCATION, SysCode.LN_BNK_LCP);
			
			// [csawi:20111005] [included for magnetic stripe compliance check]
			mcpProcess.putAppParameter(Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK,
					xmlUtil.getContent(nodeElement, MAGNETIC_STRIPE_COMPLIANCE_CHECK.getString()));
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK));
			
			// [mqueja:11/22/2011] [added message security code tag]
			mcpProcess.putAppParameter(Keys.XMLTags.MESSAGE_SECURITY_CODE,
					xmlUtil.getContent(nodeElement, MESSAGE_SECURITY_CODE.getString()));
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.MESSAGE_SECURITY_CODE.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.MESSAGE_SECURITY_CODE));
			
			// [salvaro:11/28/2011] [added magnetic stripe compliance check flag]
			mcpProcess.putAppParameter(Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG,
					xmlUtil.getContent(nodeElement, MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG.getString()));
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG));
			
			// [salvaro:11/28/2011] [added chip obs validicty check flag]
			mcpProcess.putAppParameter(Keys.XMLTags.CHIP_OBS_VALIDICTY_CHECK_FLAG,
					xmlUtil.getContent(nodeElement, CHIP_OBS_VALIDICTY_CHECK_FLAG.getString()));
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.CHIP_OBS_VALIDICTY_CHECK_FLAG.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.CHIP_OBS_VALIDICTY_CHECK_FLAG));
			
			// [mqueja:04/12/2012] [Redmine#3832] [Added Checking of Currency Code for Decimalization]
    		try {
    			FileInputStream file = new FileInputStream(xmlUtil.getContent(nodeElement, 
    					Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString()));
    			properties.load(file);
    			file.close();
    		} catch (FileNotFoundException e1) {
    			String sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "SSW2025",
    					new String[] {Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString()});

    			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, "SSW2025",
    					new String[] {Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString()});
    			return;
    		} catch (Exception e) {
    			mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "Fail to load " 
    					+ Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString());
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "Fail to load " 
    					+ Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString());
    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, e.getStackTrace().toString());
    			return;
    		}
			
			mcpProcess.putAppParameter(Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK, properties);
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.CURRENCRY_CODE_DECIMAL_CHECK));
			
			mcpProcess.putAppParameter(Keys.XMLTags.SEND_TIMEOUT_RESPONSE_FLAG, 
					xmlUtil.getContent(nodeElement, SEND_TIMEOUT_RESPONSE_FLAG.getString()));
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.SEND_TIMEOUT_RESPONSE_FLAG.getString() 
					+ ": " + parameters.getAppParam(Keys.XMLTags.SEND_TIMEOUT_RESPONSE_FLAG));
			
			
            /*
             * 04232015 ACSS)MLim Set Value for Sending to Master Card if Standin Handlin START >>
             * 
             */
            mcpProcess.putAppParameter(Keys.XMLTags.SEND_STANDIN_RESPONSE_FLAG, 
                         xmlUtil.getContent(nodeElement, SEND_STANDIN_RESPONSE_FLAG.getString()));
            
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
                         "[BanknetMsgDispatcher] Loading: " + Keys.XMLTags.SEND_STANDIN_RESPONSE_FLAG.getString() 
                         + ": " + parameters.getAppParam(Keys.XMLTags.SEND_STANDIN_RESPONSE_FLAG));
            /*
             * 04232015 ACSS)MLim Set Value for Sending to Master Card if Standin Handlin END <<
             * 
             */
			
						
			/*
			 * To be used by Common Brand Forward
			 * 
			 * [LQuirona: 08/10/2010] Redmine Issue# 852 check getNetworkStatus
			 * function in CommonBrandMCP [LQuirona: 08/09/2010] Redmine Issue#
			 * 843 Check mti values and BanknetMsgDispatcher class
			 */
			mcpProcess.putAppParameter(MCP_LOCATION, SysCode.LN_BNK_MCP);
			mcpProcess.putAppParameter(Keys.XMLTags.FORWARD_LOCATION,
					SysCode.LN_BNK_FORWARD);

			// [LSosuan:01/03/2011] Redmine Issue# 2116 MDS-Acquiring [HSM Error code 24]
			mcpProcess.putAppParameter(FEPMCP_CONFIG_FILE_INFORMATION, 
					xmlUtil.getContent(nodeElement, FEPMCP_CONFIG_FILE_INFORMATION.getString()));

			super.packager = new GenericValidatingPackager((String) 
					(mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE)));

			// Initialize HSMThalesAccess
			HSMThalesAccess.init(xmlUtil.getContent(nodeElement,
					HSM_CONFIG_FILE_CAPS.getString()), HSMUser.OTHER);

			/*
			 * Initialize Network Status by Sign-off Call
			 * IMCPProcess.getNetworkName method to get the network name. Call
			 * IMCPProcess.SetNetworkStatus method to set network status of
			 * Sign-off into shared memory. Output the System Log. {0}:Network
			 * name {1}:SysCode.INF_NET_MCP_UNAVAILABLE
			 */
			try {
				// [LSosuan: 01/13/2011] Redmine Issue# 2128 The status of MCP is not right
				mcpProcess.setNetworkStatus(QueueConfigMap.getNetworkName(
						(String) mcpProcess.getAppParameter(MCP_LOCATION)), SysCode.SIGN_OFF);

			} catch (SharedMemoryException sme) {
				args[0] = sme.getMessage();
				sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
				mcpProcess.writeAppLog(sysMsgId,LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetMsgDispatcher] execute(): End Process");
				return;
			}

		} catch (XMLParseException xpe) {
			args[0] = xpe.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,SERVER_SYSLOG_INFO_2021);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR,SERVER_SYSLOG_INFO_2021, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(xpe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetMsgDispatcher] execute(): End Process");
			return;

		} catch (ISOException isoe) {
			// SSW2027 - Parameter check error
			args[0] = isoe.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(isoe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetMsgDispatcher] execute(): End Process");
			return;

		} catch (Exception e) {
			// SSW2027 - Parameter check error
			args[0] = e.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetMsgDispatcher] execute(): End Process");
			return;
		} // End of try catch
	} // End of initialize()

	/**
	 * According to the MTI and queue header, dispatch the message to its
	 * process class.
	 * @param fepMessage The Fep Message data received from the queue
	 */
	@Override
	public void dispatchMsg(FepMessage fepMessage) {
		String dataFormatIdentifier;
		ISOMsg isoMsg = new ISOMsg();
		String sysMsgId;
		String[] args = new String[1];

		/*
		 * Get the header contents Call FepMessage.getDataFormatIdentifier()
		 * method. If Data format identifier = "10", Decode the message. If
		 * error occurs, Output the System Log.
		 * IMCPProcess.writeSysLog(Level.ERROR, "CSC2003") End the process Call
		 * judgeHead() method
		 */
		dataFormatIdentifier = fepMessage.getDataFormatIdentifier();

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetMsgDispatcher] dispatchMsg() start");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetMsgDispatcher] DFI: " + dataFormatIdentifier);

		try {

			if (SysCode.QH_DFI_ORG_MESSAGE.equals(dataFormatIdentifier)) {
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetMsgDispatcher] RAW Received: " 
						+ ISOUtil.hexString((byte[]) fepMessage.getMessageContent()));
				
				isoMsg = unpack((byte[]) fepMessage.getMessageContent());
				fepMessage.setMessageContent(isoMsg);
			} // End of if

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetMsgDispatcher] Call judgeHead()");
			judgeHead(fepMessage);

		} catch (ISOException isoe) {
			args[0] = isoe.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(isoe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetMsgDispatcher] execute(): End Process");

		} catch (AeonException ae) {
			// Unrecognized Header
			args[0] = ae.getMessage();
			sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ae));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetMsgDispatcher] execute(): End Process");

		} catch (Exception e) {
            args[0]  = e.getMessage();
            sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
            mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetMsgDispatcher] execute(): End Process");

        } // End of try catch
	}// End of dispatchMsg()

	/**
	 * Dispatches the message to its process class.
	 * @param fepMessage The Fep Message data received from the queue
	 */
	private void judgeHead(FepMessage fepMessage) throws ISOException,
			AeonException, Exception {
		
		InternalFormat internalMsg;
		ISOMsg isoMsg;
		String sourceId = null;
		String destinationId = null;
		String processingTypeIdentifier;
		String dataFormatIdentifier;
		String mti = "";
		String transactionCode = "";

		/*
		 * Get the header contents Call FepMessage.getDataFormatIdentifier()
		 * method. Call FepMessage.getProcessingTypeIdentifier() method.
		 */
		processingTypeIdentifier = fepMessage.getProcessingTypeIdentifier();
		dataFormatIdentifier = fepMessage.getDataFormatIdentifier();

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetMsgDispatcher] PTI: " + processingTypeIdentifier);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetMsgDispatcher] DFI: " + dataFormatIdentifier);

		if ((SysCode.QH_DFI_ORG_MESSAGE.equals(dataFormatIdentifier))
				&& (SysCode.QH_PTI_LCP_MSG.equals(processingTypeIdentifier))) {

			isoMsg = (ISOMsg) fepMessage.getMessageContent();

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetMsgDispatcher] ISOMsg Received: " + isoMsg);

			try {

				/*
				 * If Processing type identifier = "01" && Data format
				 * identifier = "10" Create the class by MTI value MTI Other
				 * condition new BanknetIsRequest 0100 0120 0400 0420 0190
				 * Authorization Response Negative Acknowledgment
				 * 
				 * BanknetAcResponse 0110 0410
				 * 
				 * BanknetCommand 0800 0620 0810 0820 0312 If the message MTI is
				 * not included in the above, end the process.
				 */

				mti = isoMsg.getMTI();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetMsgDispatcher] MTI: " + mti);
				
		        if (mcpProcess != null) {
		            int networkStatus = mcpProcess.getNetworkStatus(QueueConfigMap.getNetworkName((String)mcpProcess.getAppParameter(Keys.XMLTags.MCP_LOCATION)));

		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "NetworkStatus is: " + networkStatus);
		            new CommonBrandCommand(mcpProcess).signOnOff(1);
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "New Transaction Sign-on");
		        }

				if (BNK_IS_REQUEST_MTI_LIST.contains(mti)) {
	                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	                		"[BanknetMsgDispatcher] Call BanknetIsRequest()");
					new BanknetIsRequest(mcpProcess).execute(fepMessage);
				} else if (BNK_AC_RESPONSE_MTI_LIST.contains(mti)) {
	                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	                		"[BanknetMsgDispatcher] Call BanknetAcResponse()");
					new BanknetAcResponse(mcpProcess).execute(fepMessage);
				} else if (BNK_COMMAND_MTI_LIST.contains(mti)) {
	                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	                		"[BanknetMsgDispatcher] Call BanknetCommand()");
					new BanknetCommand(mcpProcess).execute(fepMessage);
				} else {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2209);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
							"[BanknetMsgDispatcher] Unrecognized MTI: " + mti);
					throw new AeonException();
				} // End of if

			} catch (ISOException isoe) {
				args[0] = isoe.getMessage();
				sysMsgId = mcpProcess.writeSysLog( LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027);
				mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(isoe));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetMsgDispatcher] execute(): End Process");
				//return;
				throw isoe;
			} // End of try catch
		      catch (AeonDBException e) {
//		          sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
//		            "SAW2020");
//		          mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, "SAW2020", null, null, e);
		          
		    	  sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
	                      "CSD7005");
	                      
		          this.mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, "CSD7005", new String[] {SysCode.BKN}, null,e);
		          
		          mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetMsgDispatcher] judgeHead : getSequence fail : AeonDBException " + 
		           FepUtilities.getCustomStackTrace(e));

		          stopProcess(false);
		          stopProcess(false);
		          return;
		        }
		        catch (SQLException e) {
		          sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "SAW2154");
		          mcpProcess.writeAppLog(sysMsgId, LogOutputUtility.LOG_LEVEL_ERROR, "SAW2154", args, null, e);
		          mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
		            "[BanknetMsgDispatcher] Judgehead : Database operation failed: " + 
		            FepUtilities.getCustomStackTrace(e));
		          stopProcess(false);
		          stopProcess(false);
		          return;
		        }
			/*
			 * If Processing type identifier <> " " && Data format identifier =
			 * "01" Create the class by Transaction Code MTI Other Condition
			 * BanknetIsResponse 0100 0120 0400 0420 0190 Authorization Response
			 * Negative Acknowledgement
			 * 
			 * PTI Transaction Code BanknetAcRequest 03 01XXXX 03 03XXXX 03
			 * 09XXXX 03 04XXXX BanknetCommand PTI Transaction Code SOURCE_ID
			 * DESTINATION_ID 31 32 33 34 36 37 03 05XXXX(Bit Off) HST BKN MTI
			 * PTI 0620 04 If Processing type identifier = "09" or "10" Create
			 * BanknetTimeout class
			 * 
			 */
		} else if (SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatIdentifier)
				&& (!SPACE.equals(processingTypeIdentifier))) {

			internalMsg = (InternalFormat) fepMessage.getMessageContent();

			// [LSosuan: 01/24/2011] Redmine Issue# 2172 Sent Echo Test command to BANKNET error
			try {

				/* Check if Internal Format is null Else If it's not null get
				 * the real values Get MTI from Internal Message
				 */
				if (null == internalMsg) {
					mti = SPACE;
					transactionCode = SPACE;
					sourceId = SPACE;
					destinationId = SPACE;

				} else {
					mti = (null == internalMsg.getValue(InternalFieldKey.OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue()) 
						? SPACE 
						: internalMsg.getValue(InternalFieldKey.OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue();
					
					transactionCode = (null == internalMsg.getValue(
							InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue()) 
						?  SPACE
						: internalMsg.getValue(
								InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue().substring(0, 2);
					
					sourceId = (null == internalMsg.getValue(InternalFieldKey.HOST_HEADER.SOURCE_ID).getValue()) 
						? SPACE
						: internalMsg.getValue(InternalFieldKey.HOST_HEADER.SOURCE_ID).getValue();
					
					destinationId = (null == internalMsg.getValue(
							InternalFieldKey.HOST_HEADER.DESTINATION_ID).getValue()) 
						? SPACE 
						: internalMsg.getValue(InternalFieldKey.HOST_HEADER.DESTINATION_ID).getValue();
				} // End of if

			} catch (Exception e) {
				args[0] = e.getMessage();
				sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,	SERVER_SYSLOG_INFO_2027);
				mcpProcess.writeAppLog(sysMsgId,LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2027, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetMsgDispatcher] execute(): End Process");
				return;
			} // End of try catch

			if ((SysCode.QH_PTI_MCP_REQ.equals(processingTypeIdentifier))
					&& (BNK_AC_REQUEST_TRANS_CODE_PREFIX_LIST .contains(transactionCode))) {
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
                		"[BanknetMsgDispatcher] Call BanknetAcRequest()");
				// Null checks before retrieving data
				new BanknetAcRequest(mcpProcess).execute(fepMessage);

			} else if ((BNK_IS_RESPONSE_MTI_LIST.contains(mti))
					&& (SysCode.QH_PTI_MCP_RES.equals(processingTypeIdentifier))) {
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
                		"[BanknetMsgDispatcher] Call BanknetIsResponse()");
				new BanknetIsResponse(mcpProcess).execute(fepMessage);
			}
			
			// [EMaranan: 03/02/2011] Redmine Issue# 2236 Apply changes in DD document.
			else if ((BNK_COMMAND_PROCESSING_TYPE_IDENTIFIER .contains(processingTypeIdentifier) 
					|| ((SysCode.QH_PTI_MCP_REQ.equals(processingTypeIdentifier))
					&& (SysCode.BKN.equals(destinationId)) 
					&& (SysCode.HST.equals(sourceId))))
					|| ((SysCode.MTI_0620.equals(mti) 
							&& SysCode.QH_PTI_MCP_RES.equals(processingTypeIdentifier)))) {
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
                		"[BanknetMsgDispatcher] Call BanknetCommand()");
				new BanknetCommand(mcpProcess).execute(fepMessage);
			
			} else {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2209);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
						"[BanknetMsgDispatcher] Unrecognized queue header.");
				throw new AeonException();
			} // End of if

		} else if (SysCode.QH_PTI_TIMEOUT.equals(processingTypeIdentifier)) {
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
            		"[BanknetMsgDispatcher] Call BanknetTimeout()");
			new BanknetTimeout(mcpProcess).execute(fepMessage);

		} else {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2209);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
					"[BanknetMsgDispatcher] Unrecognized queue header.");
			throw new AeonException();
		} // End of if
	}// End of judgeHead()

	/**
	 * This method is used by non-card brand code (e.g. MON system) to unpack
	 * messages to ISO format
     * @param message The message in bytes
     * @param packager The ISOMsg Packager
     * @return ISOMsg The unpacked bytes in ISOMsg form
	 * @throws ISOException
	 */
	public static ISOMsg unpackBAN(byte[] message,
			GenericValidatingPackager packager) throws ISOException {

		ISOMsg isoMsg;

		/*
		 * If the message contains the header Set the message header length to
		 * the packager. Create a new ISOMsg object. Set the packager to the
		 * ISOMsg object. Call the unpack method of the ISOMsg class. Return the
		 * ISOMsg.
		 */
		if ((message == null) || (packager == null)) {
			return null;
		} // End of if

		isoMsg = new ISOMsg();
		try {
			isoMsg.setPackager(packager);
			isoMsg.unpack(message);

		} catch (ISOException isoe) {
			return null;
		} // End of try catch
		return isoMsg;
	} // End of unpackBAN()
	
}// End of class
