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

import static com.aeoncredit.fep.common.SysCode.FEP_SEQUENCE;
import static com.aeoncredit.fep.common.SysCode.LN_ACE_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_BNK_LCP;
import static com.aeoncredit.fep.common.SysCode.LN_FEP_MCP;
import static com.aeoncredit.fep.common.SysCode.LN_HOST_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_MON_STORE;
import static com.aeoncredit.fep.common.SysCode.LOG_OUTPUT_SEQ;
import static com.aeoncredit.fep.common.SysCode.MTI_0100;
import static com.aeoncredit.fep.common.SysCode.MTI_0120;
import static com.aeoncredit.fep.common.SysCode.MTI_0190;
import static com.aeoncredit.fep.common.SysCode.MTI_0420;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_FEP_MESSAGE;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_ORG_MESSAGE;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_MCP_RES;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_REQ_PROC;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_SAF_REQ;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_DB_7002;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_FIND_DB;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_IO;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_SCREEN_2003;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_SCREEN_2004;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_SCREEN_7001;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.CLIENT_SYSLOG_SOCKETCOM_2003;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.COL_PROCESS_RESULT_APPROVE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.COL_PROCESS_RESULT_DECLINE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.COL_PROCESS_STATUS_APPROVED;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.EXTERNAL_RESPONSE_CODE_DUPLICATE_TRANS;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.EXTERNAL_RESPONSE_CODE_FORMAT_ERROR;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_BNK_FEP_RESPONSE_CODE_FORMAT_ERROR_DECLINED_BY_FEP;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_BNK_FEP_RESPONSE_DUPLICATE_TRANSMISSION;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_BNK_FEP_RESPONSE_MAGNETIC_COMPLIANCE_CHECK_NG;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_BNK_FEP_RESPONSE_OBS_MCHIP_VALIDITY_CHECK_NG;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_RESPONSE_CODE_3D_FAILED;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_RESPONSE_CODE_TRANSACTION_NOT_PERMITTED;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FEP_TRANSACTION_TYPE_CHECK_NG;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_02;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_05;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_07;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_80;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_90;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD22_1_PAN_ENTRY_MODE_91;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD39_RESPONSE_CODE_SUCCESS;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD3_1_TRANSACTION_TYPE_00;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD3_1_TRANSACTION_TYPE_01;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD3_1_TRANSACTION_TYPE_09;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD3_1_TRANSACTION_TYPE_28;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD48_71_1_OBS_02;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD48_71_2_OBS_I;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD48_71_2_OBS_T;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD48_82_AVS_REQUEST_INDICATOR_AVS_ONLY;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.FLD48_83_AVS_NOT_SUPPORTED;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.MSG_ERROR_PAN;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.MTI_0400;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.NEGATIVE_ACK;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.NEGATIVE_ACK_SEND_TO_HOST_FLAG_1;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.NO_SPACE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.PARTIAL_REVERSAL_INDICATOR_ON;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.PROCESS_I_O_TYPE_ISRES;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.SERVER_APPLOG_INFO_2020;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.SPACE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.STATIC_3D_VALUE_PREFIX_3;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.STATIC_3D_VALUE_PREFIX_4;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.STATIC_3D_VALUE_PREFIX_5;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.THREE_D_SECURE_RESULT_SUC;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_CHANNEL_21;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_NOT_SECURED_91;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_NOT_SUPPORTED_0;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_SUPPORTED_AND_PROVIDED_2;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_SUPPORTED_NOT_PROVIDED_1;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.UCAF_SUPPORTED_WITH_AAV_3;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.VALIDATE_NO_ERROR;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.cPAD_0;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Level;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPTransactionMgr;
import com.aeoncredit.fep.framework.exception.AeonDBException;
import com.aeoncredit.fep.framework.exception.DBNOTInitializedException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;

/**
 * Execute the Issuing Request process.According to the MTI value, pass through
 * to FEP processing or terminate the transaction.
 * 
 * @author JLiwanag
 */
public class BanknetIsRequest extends BanknetCommon {
	/** queue name */
	private String queueName = null;

	/** table domain */
	private FEPT022BnkisDomain tableDomain;

	/** dao */
	private FEPT022BnkisDAO dao;

	/** issuing log key */
	private String issuingLogKey;

	/** Banknet Internal Format process */
	private BanknetInternalFormatProcess banknetInternalFormatProcess;

	/** ISOMsg from FepMessage */
	private ISOMsg originalISOMsg;

	/** Created InternalMsg */
	private InternalFormat createdInternalMsg;
	
	/** ISOMsg reply */
	private ISOMsg replyISOMsg;

	/** PEK Value/Key Name from MCP Config */
	private String keyName;

	/** Transaction Manager */
	private FEPTransactionMgr transactionManager;

	/** Magnetic Stripe Compliance Error Indicator */
	private boolean isMagneticCompliant;

	/** Magnetic Stripe Compliance Error Indicator */
	private boolean isMChipOBSValid;

	/** Variables used for Exception Handling*/
	private String[] args   = new String[2];
	private String sysMsgID  = NO_SPACE;

	/** InternalFormat Message */
	private InternalFormat internalFormat;
	
	/** FEP Sequence number */
	private String msgProcessingNo;
	
	/**
	 * BanknetIsRequest constructor.
	 * @param mcpProcess The instance of AeonProcessImpl
	 * @param mcpProcess
	 */
	public BanknetIsRequest(IMCPProcess mcpProcess) {
		super(mcpProcess);
		try {
			banknetInternalFormatProcess = new BanknetInternalFormatProcess(mcpProcess);
			transactionManager = mcpProcess.getDBTransactionMgr();
			dao = new FEPT022BnkisDAO(transactionManager);
			tableDomain = new FEPT022BnkisDomain();

		} catch (DBNOTInitializedException dnie) {
			args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: End Process");
			return;
		}// End of try-catch

	}// End of constructor

	/**
	 * Execute the Issuing Request message.
	 * @param fepMessage The Fep Message recieved
	 */
	public void execute(FepMessage fepMessage) throws Exception {
		originalISOMsg = fepMessage.getMessageContent();

		// Return Domain for mti 0400 or 0420
		List<FEPT022BnkisDomain> returnDomain;

		// intially set to true
		isMagneticCompliant = true;
		isMChipOBSValid = true;

		// Get mti of message received
		String mti = null;
		String iField35;
		String iField36;
		String processNo;
		String timeStamp = null;
		String processIOType;
		String transactionCode;
		// String reversalTargetTransactionCode;
		// Search Issuing Log Table
		List<FEPT022BnkisDomain> resultList = null;
		String field90;
		String field90Sub1;
		String field90Sub2;
		String field90Sub3;
		String field90Sub4;
		String year;
		String key;
		FEPT022BnkisDomain searchDomain;
		String stringDomain;
		String logSequence = null;
		String queueName;
		try{
			try {
				/*
				 * Get and set the LogSequence
				 * Call mcpProcess.getSequence(SysCode.LOG_OUTPUT_SEQ) to get the LogSequence
				 * Call mcpProcess.setLogSequence(logSequence) to set the message into mcpProcess
				 * Set the LogSequence to FepMessage
				 * Setting of FEP Sequence
				 */
				logSequence = "BNK_Seq" + mcpProcess.getSequence(LOG_OUTPUT_SEQ);
				mcpProcess.setLogSequence(logSequence);

				fepMessage.setLogSequence(logSequence);
				msgProcessingNo = mcpProcess.getSequence(FEP_SEQUENCE);
				fepMessage.setFEPSeq(msgProcessingNo);

				queueName = mcpProcess.getQueueName(LN_BNK_LCP);
				// [mqueja: 06/23/2011] Added for Logging purposes
				/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, 
						"********** BRAND MCP ISSUING REQUEST START PROCESS (Seq="+ logSequence +")**********");
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving a message from " +
						"<" + queueName +">"+
						"[Process Type: " + fepMessage.getProcessingTypeIdentifier() + 
						"| Data Format: " + fepMessage.getDataFormatIdentifier() +
						"| MTI: " + originalISOMsg.getMTI() + 
						"| fepSeq: " + fepMessage.getFEPSeq() + 
						"| Network ID: BNK" + 
						"| Processing Code(first 2 digits): " + ((originalISOMsg.hasField(3)) ? 
								(originalISOMsg.getString(3).substring(0, 2)) : "Not Present")  +
								"| Response COde: " + (
										(originalISOMsg.hasField(39)) ? originalISOMsg.getString(39) : "Not Present") + "]");
				/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"BANKNET[IsRequest]: msgProcessingNo: " + msgProcessingNo);

			 
			} catch (IllegalParamException ipe) {
				args[0]  = ipe.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}  catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}// End of try catch

			// Validate the Field Presence
			try{
				mti = originalISOMsg.getMTI();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : mti: " + mti);
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}// End of try catch

			// Check the message format according to the MTI. Call CommonBrandMCP.validate() method
			int validateResult = validate(originalISOMsg);
			int specificValidate = -1;
			int complianceCheckResult = -1;
			int obsMchipValidityResult = -1;

			if(!MTI_0190.equals(mti)){
				

				try{
					/*
					 * Check for specific mandatory fields for cashing and sales.
					 * Magnetic Stripe Compliance Check (DE 48.89)
					 * OBS Mchip Validity Check (DE 48.71)
					 */
					specificValidate = specialValidate(originalISOMsg);
					
					/*
					 * [salvaro:11/28/2011] [Redmine#3405]
					 * 5. If value in MAGNETIC_STRIPE_COMPLIANCE_CHECK_FLAG = 1					
					 * Call magneticStripeComplianceCheck()
					 */
					if (Constants.MAGSTRIPE_COMPLIANCE_CHECK_FLAG_ON.equals(getMagneticStripeComplianceCheckFlag())){
							complianceCheckResult = magneticStripeComplianceCheck(originalISOMsg);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Get complianceCheckResult: " + complianceCheckResult);
							
					} // End of if MAGSTRIPE_COMPLIANCE_CHECK_FLAG_ON
					
					/*
					 * [salvaro:11/28/2011] [Redmine#3405]
					 * 6. If value in CHIP_OBS_VALIDITY_CHECK_FLAG = 1				
					 * Call mChipOBSValidityCheck
					 */
					if (Constants.CHIP_OBS_VALIDICTY_CHECK_FLAG_ON.equals(getChipObsValidictyCheckFlag())){
							obsMchipValidityResult = mChipOBSValidityCheck(originalISOMsg);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Get obsMchipValidityResult: " + obsMchipValidityResult);
					} // End of if CHIP_OBS_VALIDICTY_CHECK_FLAG_ON
					
				} catch (ISOException ie) {
					args[0]  = ie.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return;
				}// End of try catch
			}// End of if !=0190

			if ((validateResult != VALIDATE_NO_ERROR) || (specificValidate != VALIDATE_NO_ERROR)
					|| (complianceCheckResult != VALIDATE_NO_ERROR) || (obsMchipValidityResult != VALIDATE_NO_ERROR)) {

				// If error occurs, Output the System Log. IMCPProcess.writeSysLog(Level.WARN, "CSW2128") 
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetIsRequest] : Invalid ISOMsg : validateResult: " + validateResult);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetIsRequest] : Invalid ISOMsg : specficValidate: " + specificValidate);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetIsRequest] : Invalid ISOMsg : complianceCheckResult: " + complianceCheckResult);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetIsRequest] : Invalid ISOMsg : obsMchipValidityResult: " + obsMchipValidityResult);

				String fepResponseCode = "";

				/*
				 * Set appropriate internal fep response code [lquirona:20110718] [Redmine 2839 & 2838]
				 * [mqueja:09/07/2011] [added new fep response code]
				 */
				if((validateResult != VALIDATE_NO_ERROR)){		
					fepResponseCode = FEP_BNK_FEP_RESPONSE_CODE_FORMAT_ERROR_DECLINED_BY_FEP;
				}else if(specificValidate != VALIDATE_NO_ERROR){
					fepResponseCode = FEP_BNK_FEP_RESPONSE_CODE_FORMAT_ERROR_DECLINED_BY_FEP;
				}else if(complianceCheckResult != VALIDATE_NO_ERROR){
					fepResponseCode = FEP_BNK_FEP_RESPONSE_MAGNETIC_COMPLIANCE_CHECK_NG;
				}else if(obsMchipValidityResult != VALIDATE_NO_ERROR){
					fepResponseCode = FEP_BNK_FEP_RESPONSE_OBS_MCHIP_VALIDITY_CHECK_NG;
				}// End of if-else

				// [lquirona:2010/07/31] [Redmine#665] [transaction message 0190, send to only to MON_STORE queue.]
				if(!MTI_0190.equals(mti)){
					replyISOMsg = (ISOMsg) originalISOMsg.clone();
					try {
						editFormatErrorMsgReply(replyISOMsg, fepResponseCode);
						sendDeclineMessage(fepMessage, replyISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, fepResponseCode, internalFormat);
					} catch (SharedMemoryException sme) {
						args[0]  = sme.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					} catch (ISOException ie) {
						args[0]  = ie.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					}// End of try catch

					// End of process.
					return;
				}//end of if mti not equal to 0190
			}// End of if validate

			// [mqueja:09/15/2011] [Redmine#3353] [Decline Unsupported Transactions]
			boolean transcationSupported = checkTransactionType(fepMessage, originalISOMsg);

			if(!transcationSupported){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] execute(): End Process");
				return;
			}

			//[rrabaya:11/27/2014]RM#5850 Checking for Account Status Inquiry : Start
			boolean isASI = isASITransaction(originalISOMsg);
			
			//Decline Advice ASI Transaction
			if((FepUtilities.isAdviceTransaction(originalISOMsg)
					|| FepUtilities.isReAdviceTransaction(originalISOMsg))
					&& isASI){
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): isAdvice/Rev Advice ASI transaction.");
						sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_DEBUG,
								Constants.FEP_BNK_RESPONSE_CODE_INVALID_TRANSACTION, createdInternalMsg);
						return;
			}
			if(isASI && isNotAnASITransaction(fepMessage, originalISOMsg)){
				return;
			}
			//[rrabaya:11/27/2014]RM#5850 Checking for Account Status Inquiry : End
			
			// [START] [eotayde 12182014: RM 5838]
			if (!isASI && isNotAMoneySendPaymentTransaction(fepMessage, originalISOMsg)) {
				return;
			}
			// [END] [eotayde 12182014: RM 5838]
			
			// [mqueja:10/07/2011] [added checking for AVS Only transaction, then decline]
			if(checkfield48(fepMessage, originalISOMsg)){
				return;
			}
			
			//[Start][Rrabaya] RM# 6011
			
			// [mqueja:02/13/2012] [Redmine#3857] [3D Merchant should always send 3D trxns]
//			if(!threeDSecureCheck(fepMessage, originalISOMsg)){
//				return;
//			}
//			  [END][Rrabaya] RM# 6011
			try {
				//Call convert to InternalFormat process
				createdInternalMsg = banknetInternalFormatProcess.createInternalFormatFromISOMsg(originalISOMsg);
				
				/*
				 * 04222015 ACSS)MLim START >> Sending to Master Card if Standin Handling
				 * if the message does NOT come from auth OR the response code does not start in 15XXX
				 * and the stand in flag is NOT do not send to stand in... --> send to BrandLCP.
				 * Otherwise, do not send.
				 */
				String sourceId = SysCode.BKN;
				// Get the Authorization flag from sharememmory.
				int standInFlg = mcpProcess.getAuthorizationFlag(sourceId);
				// Write applog
				mcpProcess.writeAppLog(Level.DEBUG, "standInFlg is" + standInFlg);
		
				if ((SysCode.STANDIN_AUTHORIZATION == standInFlg && getSendStandinResponseFlag().equals(Constants.DO_NOT_SEND_STANDIN))
//                        // however do not drop the message when it is an advice! if advice, AUTHORIZATION_JUDGMENT_DIVISION == "3".
                        && (null == createdInternalMsg.getValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION).getValue())){
//                 
				 //[rrabaya 8/27/2015] RM#6363 [MasterCard] Update INFO logging that is being displayed when message is dropped : Start
                 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "[BanknetIsRequest] Received a message from Banknet!" +
                               " But FEP is in Stand-in Mode. Drop the message!");
                 //[rrabaya 8/27/2015] RM#6363 [MasterCard] Update INFO logging that is being displayed when message is dropped : End
//                 
                 //set timestamp for mon.
                 processNo = mcpProcess.getProcessName();
                 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : processNo: " + processNo);
                 timeStamp = mcpProcess.getSystime();
                 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : timeStamp: " + timeStamp);
                 processIOType = PROCESS_I_O_TYPE_ISRES;
                 createdInternalMsg.addTimestamp(processNo, timeStamp, processIOType);
//
//                 // set Exception Response Code.
//                 // TODO: put this in constant.
                 createdInternalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.RESPONSE_CODE, "85");
                 // TODO: put this in constant.
                 createdInternalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, "12085");

                 fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
                 fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
                fepMessage.setMessageContent(createdInternalMsg);
                 
                 // send to MON STORE.
                 queueName = mcpProcess.getQueueName(LN_MON_STORE);
                 sendMessage(queueName, fepMessage);
                //[rrabaya 08/26/2015] RM#6361 [MasterCard] Missing INFO logging that message is sent to MON_STORE : Start
 				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
 						+ mcpProcess.getQueueName(LN_MON_STORE) + ">" +
 						"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
 						"| Data format:" + fepMessage.getDataFormatIdentifier() +
 						"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
 						"| FepSeq:" + fepMessage.getFEPSeq() +
 						"| NetworkID: BNK" + 
 						"| Transaction Code(service):" + createdInternalMsg.getValue(
 								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
 						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
 						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
 						"| FEP Response Code: "+ createdInternalMsg.getValue(
 								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
 				//[rrabaya 08/26/2015] RM#6361 [MasterCard] Missing INFO logging that message is sent to MON_STORE : End
                 // send to HOST STORE.
                 // If the message is 0100 and 0400, do not send to HOST store. If 0420, send to HOSTSAF.
                 if ((!mti.trim().equals("") || mti != null) && 
                		 (mti.equals(SysCode.MTI_0420))) {
                     queueName = mcpProcess.getQueueName(LN_HOST_STORE);
                     sendMessage(queueName, fepMessage);
                 }
//                 queueName = mcpProcess.getQueueName(LN_HOST_STORE);
//                 sendMessage(queueName, fepMessage);
                 
                 return;
				}


				/*
				 * 04222015 ACSS)MLim END << Sending to Master Card if Standin Handlin 
				 * 
				 */
				
				// [mqueja:04/12/2012] [Redmine#3832] [Added Checking of Currency Code for Decimalization]
				currencyCodeCheck(createdInternalMsg, originalISOMsg);
				
				String pan = createdInternalMsg.getValue(HOST_COMMON_DATA.PAN).getValue();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Pan " + pan);
				
				// Error handling for exceptions that occurred in createInternalFormatFromISOMsg
				if(!SysCode.MTI_0190.equals(originalISOMsg.getMTI())) {
					if ((null == pan) || (pan.trim().length()==0)){
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] Error Occurred in Creating InternalFormat from ISOMsg");
		
						replyISOMsg = (ISOMsg) originalISOMsg.clone();
						try {
							editFormatErrorMsgReply(replyISOMsg,FEP_BNK_FEP_RESPONSE_CODE_FORMAT_ERROR_DECLINED_BY_FEP);
							
							sendDeclineMessage(fepMessage, replyISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, FEP_BNK_FEP_RESPONSE_CODE_FORMAT_ERROR_DECLINED_BY_FEP, createdInternalMsg);
						} catch (SharedMemoryException sme) {
							args[0] = sme.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
									"[BanknetIsRequest] execute(): End Process");
							return;
						} catch (ISOException ie) {
							args[0]  = ie.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
							return;
						}// End of try catch			
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] execute(): End Process");
						return;
					} // End of If internalFormat = null
				}
				//Add timestamp for internal format
				processNo = mcpProcess.getProcessName();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : processNo: " + processNo);
				timeStamp = mcpProcess.getSystime();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : timeStamp: " + timeStamp);
				processIOType = PROCESS_I_O_TYPE_ISRES;

				createdInternalMsg.addTimestamp(processNo, timeStamp, processIOType);

				/*
				 * Retrieve keyName from configuration file
				 * Use value set in BanknetCommon
				 */
				keyName = (String) getIssuingEncryptionInformationKeyName();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: keyName: " + keyName);

				createdInternalMsg.setValue(CONTROL_INFORMATION.PEK_NAME, keyName);
				createdInternalMsg.setValue(HOST_HEADER.MESSAGE_PROCESSING_NUMBER, fepMessage.getFEPSeq());

			} catch (SharedMemoryException sme) {
				args[0]  = sme.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}// End of try-catch

			// Error handling if cannot create transaction code (not supported transaction)
			// transaction)
			if(NO_SPACE.equals(createdInternalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue())) {

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Transaction is not supported");

				if(!MTI_0190.equals(mti)){
					replyISOMsg = (ISOMsg) originalISOMsg.clone();

					try {
						editFormatErrorMsgReply(replyISOMsg, FEP_RESPONSE_CODE_TRANSACTION_NOT_PERMITTED);
						sendDeclineMessage(fepMessage, replyISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, 
								FEP_RESPONSE_CODE_TRANSACTION_NOT_PERMITTED, internalFormat);
					} catch (SharedMemoryException sme) {
						args[0]  = sme.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					} catch (ISOException ie) {
						args[0]  = ie.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					}// End of try catch

					// End of process.
					return;
				}//end of if mti not equal to 0190
			}// End of Transaction Code = ""

			// Get Transaction Code from newly created internal Message
			transactionCode = createdInternalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: transactionCode: " + transactionCode);

			// [mvalera:2010/07/28] [Redmine#700] [if reversal messages, then set Fields 35 & 36 & 45 from Redmine#538]
			if (mti.startsWith(PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE)) {

				// [mqueja:09/16/2011] [Redmine #2595] [Partial Reversal Checking]
				boolean isPartialReversalPartial = partialReversalChecking(fepMessage, originalISOMsg);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : Partial Reversal Flag : " + isPartialReversalPartial);

				try {
					/*
					 * Get the message from execute() parameter
					 *  Retrieve these fields from received message
					 */
					field90 = originalISOMsg.getString(90);
					field90Sub1 = field90.substring(0,4); // mti
					field90Sub2 = field90.substring(4,10); // field11
					field90Sub3 = field90.substring(10,20); // field7
					field90Sub4 = (ISOUtil.padleft(field90.substring(20,31), 11, cPAD_0)); // field 32

					/*
					 * Create the key for retrieving the FepSequence from dao
					 * key = field90Sub1 + field90Sub3 + field90Sub2 + field90Sub4;
					 * 
					 * [mqueja:08/10/2011] [Redmine #3254]
					 * [Updated values of Issuing Log Key to MTI + DE2 + DE7 + DE11 + DE32 + DE37]
					 */
					key = field90Sub1 + originalISOMsg.getString(2) + field90Sub3 
					+ field90Sub2 + field90Sub4 + originalISOMsg.getString(37);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: key (reversal message): " + key);
				} catch (ISOException ie) {
					args[0]  = ie.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return;
				} 

				/*
				 * SearchDomain - holder of the network key to be used
				 * Set network key
				 * Find the domain with the network key as its primary key
				 * ReturnDomain - if record found, return domain is not null
				 */
				searchDomain = new FEPT022BnkisDomain();
				searchDomain.setNw_key(key);
				returnDomain = null;

				try{
					returnDomain = dao.findByCondition(searchDomain);
				}catch (Exception e) {
					// SQL Error
					args[0]  = e.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return;
				}// end of catch

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: returnDomain (reversal advice message) : " + returnDomain);
				if (null != returnDomain && !returnDomain.isEmpty()) {
					stringDomain = String.valueOf(returnDomain.get(0).getFep_seq());

					/*
					 * Get year from mcpProcess getSystime method
					 * If month from field90_3 is greater than month from current time,
					 * subtract 1 from YYYY
					 */
					year = timeStamp.substring(0, 4);
					if ((Integer.parseInt(field90Sub3.substring(0, 2))) > (Integer
							.parseInt(timeStamp.substring(4, 6)))) {
						year = (String.valueOf(Integer.parseInt(year) - 1));
					}// End of if

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
									"[BanknetIsRequest]: YYYY (reversal message) : " + year);

					/*
					 * YYYY + field 90.3 (MMDDhhmmss)
					 * 
					 * Fep sequence from issuing log table using key
					 * Set fields 35
					 * and 36
					 * Set transaction code of reversal target transaction to original transaction code (field 45)
					 */
					iField35 = year + field90Sub3;
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: iField_35 (reversal message) : " + iField35);

					iField36 = stringDomain;
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: iField_36 (reversal message) : " + iField36);

					createdInternalMsg.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, iField35);
					createdInternalMsg.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, iField36);
				} // End of if !domain.isEmpty()
			}//end of if reversal

			/*
			 * [Lquirona:20110808] [Redmine Issue #2914] [For reversal request check expiration date] - DELETE HOST SIDE will modify
			 * 
			 * All transactions should have no duplicate using the details 
			 *  from current message.
			 * 
			 * Check the duplicate message. 3.1 Create the check key.
			 *  key = MTI + Field 7 + Field 11 + Field 32(Except for MTI=0190) 
			 *  Call SuperClass.searchIssuingLog() method. This is not used anymore. 
			 *  If return = true End the process.
			 *  
			 *  [mqueja:] [Redmine #2840] 
			 *  [added ISOUtil.padleft(originalISOMsg.getString(32), 11, Constants.cPAD_0) instead of
			 *  originalISOMsg.getString(32)]
			 *  
			 *  [mqueja:08/10/2011] [Redmine #3254] 
			 *  [Updated values of Issuing Log Key to MTI + DE2 + DE7 + DE11 + DE32 + DE37]
			 */
			try{
				String field2 = (MTI_0190.equals(mti) ? NO_SPACE : originalISOMsg.getString(2));
				String field7 = originalISOMsg.getString(7);
				String field11 = originalISOMsg.getString(11);
				String field32 = (MTI_0190.equals(mti) ? NO_SPACE : ISOUtil.padleft(originalISOMsg.getString(32), 11, cPAD_0));
				String field37 = originalISOMsg.getString(37);

				issuingLogKey = mti + field2 + field7 + field11 + field32 + field37;

			}catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}// End of try catch
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: issuingLogKey : " + issuingLogKey);				

			// Set the network key
			tableDomain.setNw_key(issuingLogKey);

			try {
				resultList = dao.findByCondition(tableDomain);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: resultList : " + resultList);

			} catch (Exception e) {
				// SQL Error
				args[0]  = e.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}// end of catch

			if (null != resultList) {
				if ((!resultList.isEmpty())) {
					boolean isMatched = true;
					try{
						isMatched = isDuplicate(resultList, originalISOMsg);
					} catch (ISOException ie) {
						args[0]  = ie.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					}catch (ClassNotFoundException cnfe) {	
						args[0]  = cnfe.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, SERVER_APPLOG_INFO_2020, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(cnfe));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
						return;
					} catch (IOException ioe) {
						args[0]  = ioe.getMessage();
						sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_IO);
						mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_IO, args);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ioe));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					}// End of try-catch	

					if(isMatched){
						// when record already exist in the DB and F18 or F41 or F42 is all matched
						mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, CLIENT_SYSLOG_SCREEN_2003);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "BANKNET[IsRequest] transaction has duplicate! ");

						// [mqueja:07062111] [Redmine #3104] [added response code for duplicate message]
						replyISOMsg = (ISOMsg) originalISOMsg.clone();

						try{
							editISOMsgForDuplicateMessage(replyISOMsg);
							sendDeclineMessage(fepMessage, replyISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, FEP_BNK_FEP_RESPONSE_DUPLICATE_TRANSMISSION, internalFormat);
						} catch (ISOException ie) {
							args[0]  = ie.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
							return;
						} catch (SharedMemoryException sme) {
							args[0]  = sme.getMessage();
							sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
							mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
							return;
						} // End of try-catch

						return;
					}// End of F18, F41 and F42 checking
				}// End of resultList checking5
			} // end of if

			// Call judge() method.
			fepMessage.setMessageContent(originalISOMsg);

			try{
				this.judge(fepMessage);
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return;
			}catch (Exception e) {
				// SQL Error
				args[0]  = e.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
				return;
			}// end of catch
		}catch (Exception e) {
			// SQL Error
			args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
			return;
		}
	}// end of execute method

	/**
	 * Judge the message according to MTI.
	 * @param fepMessage The Fep Message received
	 * @throws ISOException
	 * @throws AeonDBException
	 * @throws IllegalParamException
	 * @throws Exception
	 */
	private void judge(FepMessage fepMessage) throws ISOException, AeonDBException, IllegalParamException, Exception {

		originalISOMsg = (ISOMsg) fepMessage.getMessageContent();
		String mti = null;

		try {
			mti = originalISOMsg.getMTI();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : judge(): mti :" + mti);

		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
			return;
		}// End of try-catch

		// If MTI = 0120 or 0420 (advice message) Call advice method.
		if (MTI_0120.equals(mti) || MTI_0420.equals(mti)) {
			try {
				this.advice(fepMessage);

			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
				return;
			}catch (Exception e) {
				// SQL Error
				args[0]  = e.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
				return;
			}// end of catch

		} else if (MTI_0190.equals(mti)) {
			this.negativeAcknowledgement(fepMessage);

		} else {
			try {
				this.passThrough(fepMessage);

			} catch (AeonDBException ade) {
				args[0]  = ade.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ade));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
				return;
			} catch (IllegalParamException ipe) {
				args[0]  = ipe.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] judge(): End Process");
				return;
			} // End of try-catch
		}// end of if-else

	}// end of judge method

	/**
	 * Pass through the message to FEP processing function.
	 * @param fepMessage The Fep Message received
	 * @throws AeonDBException
	 * @throws IllegalParamException
	 */
	private void passThrough(FepMessage fepMessage) throws AeonDBException,
	IllegalParamException {
		
		/*
		 * Write the Shared Memory(ISOMsg). 
		 *  Call getSequence() of IMCPProcess to create the FEP sequence No 1.2 
		 *  Call super.setTransactionMessage() method, write the message into Shared Memory
		 */
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : passThrough() start");
		setTransactionMessage(fepMessage.getFEPSeq(), ((Integer)getAuthorizationControlTimer()) ,fepMessage);

		/*
		 * Edit queue header Processing type identifier = "21" 
		 * Data format identifier = "01"
		 */
		fepMessage.setProcessingTypeIdentifier(QH_PTI_REQ_PROC);
		fepMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the
		 *  message to the queue of FEP processing function
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_FEP_MCP),refer to
		 *  "SS0111 System Code Definition(FEP).xls"
		 */
		fepMessage.setMessageContent(createdInternalMsg);
		queueName = mcpProcess.getQueueName(LN_FEP_MCP);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : Logsequence: " + fepMessage.getLogSequence());
		sendMessage(queueName, fepMessage);

		// [mqueja: 06/23/2011] Added for Logging purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + createdInternalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	}// End of passThrough()

	/**
	 * Execute the advice message.
	 * @param fepMessage The Fep Message received
	 * @throws ISOException
	 * @throws Exception
	 */
	private void advice(FepMessage fepMessage) throws ISOException, Exception {

		ISOMsg originalISOMsg = fepMessage.getMessageContent();
		String mti = null;

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : advice()");

		try {
			mti = originalISOMsg.getMTI();
		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
			return;
		}

		// Edit the response ISOMsg according to the request message.
		replyISOMsg = null;
		replyISOMsg = (ISOMsg) originalISOMsg.clone();

		/* [lquirona - 04082011 - Redmine Issue #2370 : 0120/0420 send to FEP MCP for Accrual Processing
		   and reply 00 in BNK MCP which means transaction completed successfully */

		if (MTI_0120.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : 0120");
			unsetMTI_0130(replyISOMsg);

			/*
			 * Set F39 to 00 - Completed successfully
			 * Set reply mti
			 */
			replyISOMsg.set(39, FLD39_RESPONSE_CODE_SUCCESS);
			replyISOMsg.setResponseMTI();
			replyISOMsg.recalcBitMap();

		} else if (MTI_0420.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : 0420");
			unsetMTI_0430(replyISOMsg);
			/*
			 * Set F39 to 00 - Completed successfully
			 * Set reply mti
			 */
			replyISOMsg.set(39, FLD39_RESPONSE_CODE_SUCCESS);
			replyISOMsg.setResponseMTI();
			replyISOMsg.recalcBitMap();
		}// End of if-else

		/*
		 * Encode the ISOMsg.
		 * [emaranan 04/11/2011 Redmine Issue # 2370] - Changes in DD applied
		 */
		replyISOMsg.set(39, FLD39_RESPONSE_CODE_SUCCESS);
		replyISOMsg.setPackager(this.packager);
        
        byte[] messageArray = pack(replyISOMsg);
        
        // If error occurs,
        if(messageArray.length < 1) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest]Error in Packing Message");
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] advice(): End Process");
                    return;
        } // End of if bytesMessage is 0

        
		fepMessage.setMessageContent(messageArray);

		/*
		 * Transmit the Response Message. 
		 *  Edit the queue header.
		 *   Processing type identifier = "04" 
		 *   Data format identifier = "10" 
		 *  Call sendMessage(String queueName, FepMessage message) to send the message to the queue of LCP.
		 *   queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),
		 *   refer to "SS0111 System Code Definition(FEP).xls"
		 */
		fepMessage.setProcessingTypeIdentifier(QH_PTI_MCP_RES);
		fepMessage.setDataFormatIdentifier(QH_DFI_ORG_MESSAGE);
		queueName = mcpProcess.getQueueName(LN_BNK_LCP);
		sendMessage(queueName, fepMessage);
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <"
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + replyISOMsg.getMTI() + 
				"| fepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| ProcessingCode(first 2 digits):" + replyISOMsg.getString(3).substring(0,2) + 
				"| Response Code: " + ((replyISOMsg.hasField(39)) ? replyISOMsg.getString(39) : "Not Present"));
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/

		/**
		 * Insert into issuing log table: 1. Network Key 2. FEP Sequence 3.
		 * Business Date 4. Process Status 5. Process Result 6. Message Data 7.
		 * Create Record Time 8. Create Record PID 9. Update Record Time 10.
		 * Update Record PID
		 */

		tableDomain.setNw_key(issuingLogKey);
		tableDomain.setFep_seq(new BigDecimal(fepMessage.getFEPSeq()));
		tableDomain.setBus_date(fepMessage.getDateTime().substring(0, 8));

		/*
		 * [lquirona:06/23/2010] [Redmine#429] [If DE39 is '30' set the return value of my method 
		 *  super.validate(isoMsg) to BIT 44]
		 */
		tableDomain.setPro_status(COL_PROCESS_STATUS_APPROVED);
		tableDomain.setPro_result(FLD39_RESPONSE_CODE_SUCCESS.equals(replyISOMsg.getString(39)) 
				? COL_PROCESS_RESULT_APPROVE 
						: COL_PROCESS_RESULT_DECLINE);

		// Message bytes already set as message content before this
		tableDomain.setMsg_data(((byte[]) fepMessage.getMessageContent()));

		// [fsamson:07/21/2010] [Redmine#649] [When applying domain.setUpdateId, use ProcessName to set the item.]
		tableDomain.setUpdateId(mcpProcess.getProcessName()); 

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Nw_key:" + tableDomain.getNw_key());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Bus_date:" + tableDomain.getBus_date());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Fep_seq:"+ tableDomain.getFep_seq());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Pro_status:" + tableDomain.getPro_status());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Pro_result:" + tableDomain.getPro_result());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "BANKNET[IsRequest] UpdateId:" + tableDomain.getUpdateId());

		/*
		 * Call FEPT022BnkisDAO.insert() to insert the message Call 
		 *  SuperClass.insertLogTable() to insert the message the DAO name is
		 *  refers to [BD Document][FEP] 7-1 Logical Table List.xls
		 */
		try {
			dao.insert(tableDomain);
			transactionManager.commit();
		} catch (Exception e) {
			mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2004);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : insert in advice() FAIL. ");
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
			transactionManager.rollback();
			throw e;
		}// End of try-catch
		/**[rrabaya 08042014] RM#5721 Start: Advice does not set Original Transaction Date Time and Original Fep Sequence Number */
		//createdInternalMsg.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, originalISOMsg.getString(7));
		//createdInternalMsg.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, fepMessage.getFEPSeq());
		createdInternalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.ORIGINAL_TRANSACTION_CODE,
				(createdInternalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.ORIGINAL_TRANSACTION_CODE)).getValue());
		/**[rrabaya 08042014] RM#5721 END: Advice does not set Original Transaction Date Time and Original Fep Sequence Number */

		/*
		 * Edit the queue header. 
		 *  Processing type identifier = "05" 
		 *  Data format identifier = "01"
		 */
		fepMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
		fepMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the message 
		 *  to the queue of Monitor STORE process.
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 *  
		 * [lsosuan:12132010] [Redmine #2059] [Banknet MCP Exception occurs when IC data is invalid]
		 */
		fepMessage.setMessageContent(createdInternalMsg);
		queueName = mcpProcess.getQueueName(LN_MON_STORE);
		sendMessage(queueName, fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + createdInternalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		/*
		 * Call sendMessage(String queueName, FepMessage message)
		 * to send the message to the queue of ACE STORE process.
		 * queueName=mcpProcess.getQueueName(SysCode.LN_ACE_STORE),
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 * 
		 * [lsosuan:12132010] [Redmine #2059] [Banknet MCP Exception occurs when IC data is invalid]
		 */
		queueName = mcpProcess.getQueueName(LN_ACE_STORE);
		sendMessage(queueName, fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + createdInternalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the
		 *  message to the queue of HOST STORE process.
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_HOST_STORE),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 * 
		 * [lsosuan:12132010] [Redmine #2059] [Banknet MCP Exception occurs when IC data is invalid]
		 */
		queueName = mcpProcess.getQueueName(LN_HOST_STORE);
		sendMessage(queueName, fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "BANKNET[IsRequest] : message sent : " + queueName);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ mcpProcess.getQueueName(LN_HOST_STORE) + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + createdInternalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		/*
		 * Edit the queue header. 
		 *  Processing type identifier = "21" 
		 *  Data format identifier = "01"
		 */
		fepMessage.setProcessingTypeIdentifier(QH_PTI_REQ_PROC);
		fepMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the message to the queue of 
		 *  FEP processing function.
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_FEP_MCP),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 */
		queueName = mcpProcess.getQueueName(LN_FEP_MCP);
		sendMessage(queueName, fepMessage);

		InternalFormat internalFormat = (InternalFormat) fepMessage.getMessageContent();

		// [mqueja: 06/23/2011] Added for Logging purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| Response Code: "+ internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

	}// End of advice()

	/**
	 * Negative acknoledgement process
	 * @param fepMessage The Fep Message received
	 */
	private void negativeAcknowledgement(FepMessage fepMessage) {

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: negativeAcknowledgement()");

		/*
		 * Output the transaction history log. 
		 *  Edit the queue header.
		 *   Processing type identifier = "05" 
		 *   Data format identifier = "01" 2.2
		 *   
		 * [lsosuan:12132010] [Redmine#2059] [Banknet MCP Exception occurs when IC data is invalid]
		 */
		fepMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
		fepMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
		createdInternalMsg.setValue(HOST_COMMON_DATA.PAN, NEGATIVE_ACK);
		fepMessage.setMessageContent(createdInternalMsg);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the message to the 
		 *  queue of Monitor STORE process.
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),
		 *  refer to SS0111 System Code Definition(FEP).xls"
		 */
		queueName = mcpProcess.getQueueName(LN_MON_STORE);
		sendMessage(queueName, fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ queueName + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + createdInternalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		/*
		 * Write the HOST SAF
		 * Call sendMessage(String queueName, FepMessage message) to send the message to the 
		 *  queue of HOST STORE process.
		 *  queueName=mcpProcess.getQueueName(SysCode.LN_HOST_STORE),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 *  
		 * [lsosuan:12132010] [Redmine #2059] [Banknet MCP Exception occurs when IC data is invalid]
		 *
		 * Check Negative Acknowledgment Send to HOST flag. (0=not send,1=send to HOST)
		 * [lquirona:07/11/2011] [Redmine#3106] [Check send to HOST SAF Flag]
		 */
		if(NEGATIVE_ACK_SEND_TO_HOST_FLAG_1.equals(getNegativeAckSendToHostFlag())){
			queueName = mcpProcess.getQueueName(LN_HOST_STORE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Logsequence: " + fepMessage.getLogSequence());
			sendMessage(queueName, fepMessage);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
					+ queueName + ">" +
					"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMessage.getDataFormatIdentifier() +
					"| MTI:" + createdInternalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMessage.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + createdInternalMsg.getValue(
							HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
							"| SourceID: " + createdInternalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
							"| Destination ID: " + createdInternalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
							"| FEP Response Code: "+ createdInternalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		}// End of negative acknowledgment send to HOST flag
	}// end of negativeAcknowledgment

	/**
	 * Helper method to unset fields in reply ISOMsg when format error occurs.
	 * @param replyISOMsg The ISOMsg to be modified
	 * @param fepCode The Fep Response Code
	 * @throws ISOException
	 */
	private void editFormatErrorMsgReply(ISOMsg replyISOMsg, String fepCode)
			throws ISOException {
		/*
		 * [csawi 20111007] edited list of parameters, added fepCode so as
		 * external code and fep response code
		 */
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: editFormatErrorMsgReply()");

		// The index where validation failed
		String validateResult = SPACE;

		// [mqueja:06/30/2011] [Redmine #2838] [Response Code for format error is 30]
		if(isMagneticCompliant == false || isMChipOBSValid == false){
			replyISOMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
		}else{
			/*
			 * Format Error Code Redmine Issue #429
			 * Set the format error code in Bit 44
			 */
			String fepResCode =  super.changeToExternalCode(SysCode.NETWORK_BKN, fepCode);
			//[rrabaya 05/21/2015] RM#6146 [MasterCard] Invalid external response code in MONSYS for Format Error
			//if(null == fepResCode || fepResCode.trim().length() > 0){
			//	fepResCode = EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR;
			//}
			if(null == fepResCode || fepResCode == NO_SPACE){
				fepResCode = EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR;
			}
			//[rrabaya 05/21/2015] RM#6146 End
			replyISOMsg.set(39, fepResCode);
			replyISOMsg.set(44, validateResult);
		}

		// Unset fields depending on the response message
		if(MTI_0100.equals(replyISOMsg.getMTI())){
			unsetMTI_0110(replyISOMsg);
		}else if(MTI_0420.equals(replyISOMsg.getMTI())){
			unsetMTI_0430(replyISOMsg);
		}else if(MTI_0120.equals(replyISOMsg.getMTI())){
			unsetMTI_0130(replyISOMsg);
		}else if(MTI_0400.equals(replyISOMsg.getMTI())){
			unsetMTI_0410(replyISOMsg);// End of else if
		}
		// [MAguila 07-26-2011] Added setting of packager
		GenericValidatingPackager p = new GenericValidatingPackager(getPackageConfigurationFile());
		replyISOMsg.setPackager(p);

		// Set reply mti
		replyISOMsg.recalcBitMap();

	}// End of editFormatErrorMsgReply()

	/**
	 * Helper method to edit ISO Message when Transaction has a duplicate
	 * @param replyISOMsg The ISOMsg to be modified
	 * @throws ISOException
	 */
	private void editISOMsgForDuplicateMessage(ISOMsg replyISOMsg) throws ISOException {

		replyISOMsg.set(39, EXTERNAL_RESPONSE_CODE_DUPLICATE_TRANS);

		// Unset fields depending on the response message
		if(MTI_0100.equals(replyISOMsg.getMTI())){
			unsetMTI_0110(replyISOMsg);
		}else if(MTI_0420.equals(replyISOMsg.getMTI())){
			unsetMTI_0430(replyISOMsg);
		}else if(MTI_0120.equals(replyISOMsg.getMTI())){
			unsetMTI_0130(replyISOMsg);
		}else if(MTI_0400.equals(replyISOMsg.getMTI())){
			unsetMTI_0410(replyISOMsg);
		}// End of else if


		// [MAguila 07-26-2011] Added setting of packager
		GenericValidatingPackager p = new GenericValidatingPackager(getPackageConfigurationFile());
		replyISOMsg.setPackager(p);

		// Set reply mti
		replyISOMsg.recalcBitMap();	
	}// End of editISOMsgForDuplicateMessage()
	
	/**
	 * Helper method for checking if Partial Reversal is Supported or not
	 * @param fepMessage The Fep Message received
	 * @param originalISOMsg The ISOMsg received
	 * @boolean True if Supported, otherwise False
	 */
	private boolean partialReversalChecking(FepMessage fepMessage, ISOMsg originalISOMsg) {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] PartialReversalChecking() start");

		String field95;
		
		if(originalISOMsg.hasField(95)){
			field95 = originalISOMsg.getString(95);
			String field95Sub1 = field95.substring(0,12);
			String amount = originalISOMsg.getString(4);

			if(Integer.parseInt(field95Sub1) >= Integer.parseInt(amount)){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: PartialReversalChecking() :" +
				" DE 4 is not greater than DE 95");

				try {
					sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, 
							FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57, internalFormat);
				} catch (SharedMemoryException sme) {
					args[0]  = sme.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return false;
				} catch (ISOException ie) {
					args[0]  = ie.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return false;
				}
			}

			String partialReversalFlag = getPartialReversalIndicator();

			if(!partialReversalFlag.equals(PARTIAL_REVERSAL_INDICATOR_ON)){
				try {
					sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, 
							FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57, internalFormat);

				} catch (SharedMemoryException sme) {
					args[0]  = sme.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return false;
				} catch (ISOException ie) {
					args[0]  = ie.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
					return false;
				}
			}
			return true;
		}
		return false;
	}// End of PartialReversalChecking()

	/**
	 * Helper method for checking if Transaction Type is Supported or not
	 * @param fepMessage The Fep Message received
	 * @param originalISOMsg The ISOMsg received
	 * @return boolean True if transaction type is supported, otherwise False
	 */
	private boolean checkTransactionType(FepMessage fepMessage, ISOMsg originalISOMsg) {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] checkTransactionType() start");
		String[] transactionType = getSupportedTransactionType().split(",");
		String field3Sub1 = (originalISOMsg.hasField(3)) ? 
				originalISOMsg.getString(3).substring(0, 2) : "";

		boolean isSupported = false;
		try {
			if (!SysCode.MTI_0190.equals(originalISOMsg.getMTI())) {
				for (String transacCode : transactionType) {
					if(transacCode.equals(field3Sub1)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] supported");
						isSupported = true;
						break;
					}
				}
			} else {
					return true;
			}

			if(!isSupported){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] checkTransactionType() is not supported");
				unsetMTI_0110(originalISOMsg);
				originalISOMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
				originalISOMsg.recalcBitMap();
				sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, FEP_TRANSACTION_TYPE_CHECK_NG, internalFormat);
				return false;
			}
		} catch (SharedMemoryException sme) {
			args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
			return false;
		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
			return false;
		}
		return true;
	}// End of checkTransactionType()

	/**
	 * Helper method to unset fields for response message with
	 * mti = 0110.
	 * @param replyISOMsg The ISOMsg to be modified
	 */
	private void unsetMTI_0110(ISOMsg replyISOMsg){
		// [Redmine #3223] [lquirona-07/29/2011: Unset fields 23, 52 and 53 in response]
		int mti0110Fields[] = {12, 13, 14, 18, 22, 23, 26, 35, 52, 53, 55, 42, 43, 61};

		replyISOMsg.unset(mti0110Fields);
		
		//[START] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
		if(replyISOMsg.hasField(48)){
			unsetSubField48(replyISOMsg);
		}
		//[END] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
	}// End of unsetMTI_0110

	/**
	 * Helper method to unset fields for response message with
	 * mti = 0130.
	 * @param replyISOMsg The ISOMsg to be modified
	 * @throws ISOException
	 */
	private void unsetMTI_0130(ISOMsg replyISOMsg) throws ISOException{
		// The index where validation failed
		String validateResult = SPACE;

		int mti0130Fields[] ={12, 13, 14, 18, 22, 26, 35, 38, 42, 43, 45, 48, 52, 53, 54, 
				55, 60, 61, 102, 103,108 , 112, 120, 121, 123, 124, 125, 127};

		replyISOMsg.unset(mti0130Fields);

		/*
		 * [lquirona:06/23/2010] [Redmine#429] [If DE39 is '30' set the return value of my method 
		 *  super.validate(isoMsg) to BIT 44]
		 */
		if (EXTERNAL_RESPONSE_CODE_FORMAT_ERROR.equals(replyISOMsg.getString(39))) {
			replyISOMsg.set(44, ISOUtil.padleft(validateResult, 3, cPAD_0));
		} else {
			replyISOMsg.unset(44);
		}
		replyISOMsg.unset(45);

	}// End of unsetMTI_0130()

	/**
	 * Helper method to unset fields for response message with
	 * mti = 0430.
	 * @param replyISOMsg The ISOMsg to be modified
	 * @throws ISOException
	 */
	private void unsetMTI_0430(ISOMsg replyISOMsg)throws ISOException{
		// The index where validation failed
		String validateResult = SPACE;

		int mti0430Fields[] = {12, 13, 14, 18, 22, 26, 35, 38, 42, 43, 45, 48, 
				52, 53, 54, 55, 60, 61, 102, 103, 108, 112, 120, 121, 123, 124, 125, 23};

		replyISOMsg.unset(mti0430Fields);

		/*
		 * [lquirona:06/23/2010] [Redmine#429] [If DE39 is '30' set the return value of my method 
		 *  super.validate(isoMsg) to BIT 44]
		 */
		if (EXTERNAL_RESPONSE_CODE_FORMAT_ERROR.equals(replyISOMsg.getString(39))) { 
			replyISOMsg.set(44, ISOUtil.padleft(validateResult, 3, cPAD_0));
		} else {
			replyISOMsg.unset(44);
		}
	}// End of unsetMTI_0430()

	/**
	 * Helper method to unset fields for response message with
	 * mti = 0410.
	 * @param replyISOMsg The ISOMsg to be modified
	 * @throws ISOException
	 */
	private void unsetMTI_0410(ISOMsg replyISOMsg)throws ISOException{
		// The index where validation failed
		String validateResult = SPACE;

		int mti0410Fields[] = {12, 13, 14, 18, 22, 23, 26, 35, 38, 42, 43, 45, 
				52, 53, 54, 55, 60, 61, 102, 103, 112, 120, 121, 123, 124, 125, 127};

		replyISOMsg.unset(mti0410Fields);

		/*
		 * [lquirona:06/23/2010] [Redmine#429] [If DE39 is '30' set the return value of my method 
		 *  super.validate(isoMsg) to BIT 44]
		 */
		if (EXTERNAL_RESPONSE_CODE_FORMAT_ERROR.equals(replyISOMsg.getString(39))) { 
			replyISOMsg.set(44, ISOUtil.padleft(validateResult, 3, cPAD_0));
		} else {
			replyISOMsg.unset(44);
		}
		
		//[START] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
		if(replyISOMsg.hasField(48)){
			unsetSubField48(replyISOMsg);
		}
		//[END] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
		
	}// End of unsetMTI_0410()

	/**
	 * [lquirona 20110621 - For special checking - Redmine Issue 2839, 2838]
	 * Helper method for specific checking of mandatory fields.
	 * @param originalISOMsg The ISOMsg received
	 * @return int The missing field number
	 * @throws ISOException
	 */
	private int specialValidate(ISOMsg originalISOMsg) throws ISOException{

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: specialValidate()");

		int value = -1;
		int CASHING_MANDATORY_FIELDS[] = {41};
		int SHOP_CASHBACK_PYMNT_MANDATORY_FIELDS[] = {42};
		
		//[Start Drevano 4/23/2015 RM# 6076]
//		int POS_ENTRY_MODE_CARD_READ_REQUIRED_FIELDS[] = {18,41,42};
		List<String> SHOP_CASHBACK_PYMNT_PROC_CODES_LIST
		= Arrays.asList(new String[] {FLD3_1_TRANSACTION_TYPE_00, FLD3_1_TRANSACTION_TYPE_09, 
				FLD3_1_TRANSACTION_TYPE_28});
//		List<String> POS_ENTRY_CARD_READ_LIST
//		= Arrays.asList(new String[] {FLD22_1_PAN_ENTRY_MODE_02, FLD22_1_PAN_ENTRY_MODE_05, 
//				FLD22_1_PAN_ENTRY_MODE_07, FLD22_1_PAN_ENTRY_MODE_80, FLD22_1_PAN_ENTRY_MODE_90, FLD22_1_PAN_ENTRY_MODE_91});
//		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Check required fields per processsing code. ");
  
		/*
		 * If transaction is ATM
		 * Check MTI [lquirona:08/26/2011] [added checking of mti so advice messages won't pass]/2011]
		 */
		if((!MTI_0120.equals(originalISOMsg.getMTI())) 
				&& (!MTI_0420.equals(originalISOMsg.getMTI()))){
			if(FLD3_1_TRANSACTION_TYPE_01.equals(originalISOMsg.getString(3).substring(0, 2))){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: Check specific fields for Cashing. ");
				for(int i : CASHING_MANDATORY_FIELDS){
					if(!originalISOMsg.hasField(i)){
						value = i;
					}
				}// End of for loop
			}// Mandatory check for cashing
			if(SHOP_CASHBACK_PYMNT_PROC_CODES_LIST.contains(originalISOMsg.getString(3).substring(0, 2))){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: Check specific fields for Shopping, CashBack, Payment. ");
				for(int i : SHOP_CASHBACK_PYMNT_MANDATORY_FIELDS){
					if(!originalISOMsg.hasField(i)){
						value = i;
					}
				}// End of loop
			}// End of SHOP_CASHBACK_PYMNT_PROC_CODES checking
//			if(POS_ENTRY_CARD_READ_LIST.contains(originalISOMsg.getString(22).substring(0, 2))){
//				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: Check specific fields for Card Read Transactions. ");
//				for(int i : POS_ENTRY_MODE_CARD_READ_REQUIRED_FIELDS){
//					if(!originalISOMsg.hasField(i)){
//						value = i;
//					}
//				}// End of loop
//			}// End of MANUAL_ENTRY_LIST checking
			
			//[END Drevano 4/23/2015 RM#6076]
			
			/*if(FLD22_1_PAN_ENTRY_MODE_01.equals(originalISOMsg.getString(22).substring(0, 2))){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: Check specific fields for Manual Transactions. ");
				for(int i : MANUAL_MANDATORY_FIELDS){
					if(!originalISOMsg.hasField(i)){
						value = i;
					}
				}// End of loop
			}*/
		}
		return value;
	}// End of specialValidate()

	/**
	 * Helper method to check Magnetic Stripe Compliance Error Indicator (F48.89)
	 * @param originalISOMsg The ISOMsg received
	 * @throws ISOException
	 * @return int Field 48
	 */
	private int magneticStripeComplianceCheck(ISOMsg originalISOMsg) throws ISOException{

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: magneticStripeComplianceCheck()");
		
		int value = -1;
		List<String> MAG_STRIPE_ERROR_INDICATOR_LIST
		= Arrays.asList(getMagneticStripeComplianceCheck().split(","));

		// Check MTI [mqueja:07/05/2011] [added checking of mti so advice messages won't pass]
		if((!MTI_0120.equals(originalISOMsg.getMTI())) 
				&& (!MTI_0420.equals(originalISOMsg.getMTI()))){
			// Check presence of field 48.
			if(originalISOMsg.hasField(48)){
				ISOMsg innerField = (ISOMsg)originalISOMsg.getValue(48);
				//Check presence of Field 48.89
				if(innerField.hasField(89)){
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]:  DE 48.89 Magnetic Stripe Compliance Error Indicator is present.");
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]:  DE 48.89 = " + innerField.getString(89));
					if(MAG_STRIPE_ERROR_INDICATOR_LIST.contains(innerField.getString(89))){
						isMagneticCompliant = false;
						value = 48;
					}
				}
			}// Check field 48
		}
		return value;
	}// End of magneticStripeComplianceCheck()

	/**
	 * Helper method to check if MChip OBS Processing checking results are invalid.
	 * @param originalISOMsg The ISOMsg received
	 * @throws ISOException
	 * @return int Field 48
	 */
	private int mChipOBSValidityCheck(ISOMsg originalISOMsg) throws ISOException{

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: mChipOBSValidityCheck()");

		int value = -1;
		// Check MTI [mqueja:07/26/2011] [added checking of mti so advice messages won't pass]
		if((!MTI_0120.equals(originalISOMsg.getMTI())) 
				&& (!MTI_0420.equals(originalISOMsg.getMTI()))){

			// Check presence of field 48.
			if(originalISOMsg.hasField(48)){
				ISOMsg innerField48 = (ISOMsg)originalISOMsg.getValue(48);

				if(innerField48.hasField(71)){
					String innerField48Inner71 = (String)innerField48.getValue(71);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: innerField48Inner71: " + innerField48Inner71);

					if((FLD48_71_1_OBS_02 + FLD48_71_2_OBS_T).equals(innerField48Inner71.substring(0,3))
							|| (FLD48_71_1_OBS_02 + FLD48_71_2_OBS_I).equals(innerField48Inner71.substring(0,3))){
						isMChipOBSValid = false;
						value = 48;
					}
				}// End of checking DE 48.71
			}// End of checking of presence of field 48
		}// End of checking of mti

		return value;
	}// End of mChipOBSValidityCheck()

	/**
	 * Helper method for additional duplicate transactions checking
	 * @param listResult Contains the queried data from the database
	 * @param originalISOMsg The ISOMsg received
	 * @return boolean True if has a record, otherwise False
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws ISOException
	 */
	private boolean isDuplicate(List<FEPT022BnkisDomain> resultList, ISOMsg originalISOMsg)
	throws IOException, ClassNotFoundException, ISOException {

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: isDuplicate()");

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: isDuplicate(): Checking if F2, F4, F18, F41 and F42 are matched ");

		byte[] b = resultList.get(0).getMsg_data();
		System.out.println(ISOUtil.hexString(b));
		ISOMsg checkDuplicate = new ISOMsg();
		checkDuplicate.setPackager(new GenericValidatingPackager(getPackageConfigurationFile()));
		checkDuplicate.unpack(b);

		String pan = (checkDuplicate.hasField(2)) ? checkDuplicate.getString(2) : NO_SPACE;
		String amount = (checkDuplicate.hasField(4)) ? checkDuplicate.getString(4) : NO_SPACE;
		String terminalID = (checkDuplicate.hasField(41)) ? checkDuplicate.getString(41) : NO_SPACE;

		boolean isMatched = true;

		if(originalISOMsg.hasField(2)){
			if(!originalISOMsg.getString(2).equals(pan.trim())){
				isMatched = false;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F2 = " + originalISOMsg.getString(2) + " pan = " + pan);
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F2 is matched ");
		}// End of F18 checking

		if(originalISOMsg.hasField(4)){
			if(!originalISOMsg.getString(4).equals(ISOUtil.padleft(amount.trim(), 12, '0'))){
				isMatched = false;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F4 = " + originalISOMsg.getString(4) + " amount = " 
						+ ISOUtil.padleft(amount.trim(), 12, '0'));
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F4 is matched ");
		}// End of F18 checking

		/*if(originalISOMsg.hasField(18)){
			if(!originalISOMsg.getString(18).equals(merchantCategoryCode)){
				isMatched = false;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F18 = " + originalISOMsg.getString(18) + " mcc = " + merchantCategoryCode);
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F18 is matched ");
		}*/// End of F18 checking

		if(originalISOMsg.hasField(41)){
			if(!originalISOMsg.getString(41).equals(terminalID)){
				isMatched = false;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F41 = " + originalISOMsg.getString(41) + " terminalId = " + terminalID);
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F41 is matched ");
		}// End of F41 checking

		/*if(originalISOMsg.hasField(42)){
			if(!originalISOMsg.getString(42).equals(cardAcceptorIDCode)){
				isMatched = false;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F42 = " + originalISOMsg.getString(42) 
						+ " card acceptor ID Code = " + cardAcceptorIDCode);

			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: F42 is matched ");
		}*/// End of F42 checking

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: isMatched? " + isMatched);
		return isMatched;
	}// End of isDuplicate()

	/**
	 * The method will send Decline Message back to Brand
	 * @param fepMessage The Fep Message received
	 * @param replyISOMsg The Reply ISOMsg
	 * @param level The level type
	 * @param fepResponse The Fep Response Code
	 * @param internalFormat The Internal Format
	 * @throws ISOException 
	 * @throws SharedMemoryException 
	 */
	private void sendDeclineMessage(FepMessage fepMessage, ISOMsg replyISOMsg, Level level, String fepResponse, 
			InternalFormat internalFormat) throws ISOException, SharedMemoryException {

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: sendDeclineMessage()");
		BanknetInternalFormatProcess banknetInFormat = new BanknetInternalFormatProcess(mcpProcess);

		// [mqueja:07/26/2011] [update code for 0120 fields in monitoring system]
		if(null == internalFormat) {

			/*
			 * [msayaboc 07-08-11] Redmine Issue #2575 POS if F37 is present and no F41 the transaction should be decline
			 * Call createInternalFormatFromISOMsg() method from Base1InternalFormatProcess to provide the needed field info
			 */
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] Method Start: created new internalformat message");
			internalFormat = banknetInFormat.createInternalFormatFromISOMsg(replyISOMsg);
			
			// [lquirona:04/13/2012] [Redmine#3832] [Added Checking of Currency Code for Decimalization in sendDecline]
			currencyCodeCheck(internalFormat, replyISOMsg);
			
		} // End of IF statement

		// Add time stamp This is important for monitoring display
		internalFormat.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
				Constants.PROCESS_I_O_TYPE_ISREQ);
		
		internalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepResponse);
		internalFormat.setValue(HOST_COMMON_DATA.RESPONSE_CODE, replyISOMsg.getString(39));
		
		// Redmine #3391 need to set message processing number for SAF acknowledment
		internalFormat.setValue(HOST_HEADER.MESSAGE_PROCESSING_NUMBER, msgProcessingNo);
		
		// [eotayde 01062015: RM 5838] Added modification for format error message
		editFormatErrorMsgReply(replyISOMsg, fepResponse);
		
		/* 1. Sending a decline message back to Brand
		 *    1.1 Create the decline message
		 *    1.2 Edit the queue header.
		 *        Processing type identifier = "04"
		 *        Data format identifier = "10"
		 *    1.3 Call sendMessage(String queueName, FepMessage message) to send the message to the queue of LCP.
		 *        *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),refer to "SS0111 System Code Definition(FEP).xls" */
		// Edit MTI
		replyISOMsg.setResponseMTI();

		replyISOMsg.setPackager(packager);

		// Recalculate isoMsg before packing; set the header contents.
		replyISOMsg.recalcBitMap();

		String sMTI = "";
		try {
			sMTI = replyISOMsg.getMTI();
		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_7001);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_7001, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]: End Process");
			throw ie;
		}// End of try catch

		/* 2.Output the Transaction History Log(Only the header of Internal format).
		 *   2.1 Edit the queue header
		 *       Processing type identifier = "05"
		 *       Data format identifier = "01"
		 *   2.2 Call sendMessage(String queueName, FepMessage message) to send the message to the queue of Monitor STORE process
		 *       *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer to "SS0111 System Code Definition(FEP).xls" */
		
        byte[] messageArray = pack(replyISOMsg);
        
        // If error occurs,
        if(messageArray.length < 1) {
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest]Error in Packing Message");
                    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsRequest] sendDeclineMessage(): End Process");
                    return;
        } // End of if bytesMessage is 0

        
        fepMessage.setMessageContent(messageArray);
		fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
		fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"Sending decline message to <"+ 
		mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + sMTI + 
				"| fepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| ProcessingCode(first 2 digits):" + ((replyISOMsg.hasField(3)) ? replyISOMsg.getString(3).substring(0,2) : "Not Present") + 
				"| Response Code: " + ((replyISOMsg.hasField(39)) ? replyISOMsg.getString(39) : "Not Present"));
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
		fepMessage.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMessage.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);
		fepMessage.setMessageContent(internalFormat);
		
		// [mqueja:09212011] [added send to HOST]
		sendMessage(mcpProcess.getQueueName(LN_HOST_STORE), fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending decline message to <"+ mcpProcess.getQueueName(LN_HOST_STORE) + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormat.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| Response Code: "+ internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		
		String pan = internalFormat.getValue(HOST_COMMON_DATA.PAN).getValue();
		
		// Updated the value in Pan for monitoring screen display if pan has no value
		if ((null == pan) || (pan.trim().length()==0)){
			internalFormat.setValue(HOST_COMMON_DATA.PAN, MSG_ERROR_PAN);
		}
		
		// csawi 20111004: set internal format to fepmessage
		fepMessage.setMessageContent(internalFormat);
		
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"Sending decline message to <"+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMessage.getDataFormatIdentifier() +
				"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| fepSeq:" + fepMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID:" + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| DestinationId:" + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() + 
				"| FEP response Code:" + internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

	}// End of sendDeclineMessage()
	
	/**
	 * Checking for AVS Only Transaction
	 * Decline if DE 48 SE 82 = 51
	 * @param fepMessage The received Fep Message
	 * @param originalISOMsg The received ISOMsg
	 * @return boolean True if Not Supported, otherwise False
	 */
	private boolean checkfield48(FepMessage fepMessage, ISOMsg originalISOMsg) {
		
		ISOMsg field48Component;
		
		if(originalISOMsg.hasField(48)){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : Has field 48");
			try {
				field48Component = (ISOMsg) originalISOMsg.getValue(48);
			
				if(field48Component.hasField(82)){
					String AVSRequestIndicator = NO_SPACE;
					AVSRequestIndicator = field48Component.getString(82);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : AVS Request Indicator: " + AVSRequestIndicator);
					
					if(FLD48_82_AVS_REQUEST_INDICATOR_AVS_ONLY.equals(AVSRequestIndicator)){
						field48Component.set(83, FLD48_83_AVS_NOT_SUPPORTED);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : Field 48.83: " + field48Component.getString(83));
						
						editFormatErrorMsgReply(originalISOMsg, FEP_RESPONSE_CODE_TRANSACTION_NOT_PERMITTED);						
						sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_ERROR, FEP_RESPONSE_CODE_TRANSACTION_NOT_PERMITTED, internalFormat);
						return true;
					
					}
				}// End of AVS Request Handling
			
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return true;
			} catch (SharedMemoryException sme) {
				args[0]  = sme.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
				return true;
			}
		}
		return false;
	}// End of checkfield48()
	
	/**
	 * Checking 3D Secure Fields
	 * Decline if 3D Secure merchant never sends a 3D secure transaction
	 * @param fepMessage The received Fep Message
	 * @param originalISOMsg The received ISOMsg
	 * @return boolean True no Error on data, otherwise False
	 */
	private boolean threeDSecureCheck(FepMessage fepMessage, ISOMsg originalISOMsg) {
		
		boolean hasError = false;
		ISOMsg field48Elements = null;
		try {
			field48Elements = (ISOMsg) originalISOMsg.getValue(48);
			
			if(originalISOMsg.hasField(48) && field48Elements.hasField(42)){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetIsRequest]: threeDSecureCheck() start");
					
				String secureCode = field48Elements.getString(42).trim();
				
				// If merchant supports 3D but do not send 3D value, decline
	            if (secureCode.endsWith(UCAF_SUPPORTED_NOT_PROVIDED_1)){
	                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	                		"[BanknetIsRequest]: threeDSecureCheck() 3D Merchant did not send a 3D data");
	                originalISOMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
	                sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_WARNING, 
							FEP_RESPONSE_CODE_3D_FAILED, internalFormat);
	                return hasError;
	            }
				
	            else if(field48Elements.hasField(43) && 
						(!secureCode.substring(4, 6).equals(UCAF_NOT_SECURED_91) 
								&& !secureCode.substring(4, 6).equals(UCAF_CHANNEL_21))) {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetIsRequest]: threeDSecureCheck() ECommerce indicator: " + secureCode);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetIsRequest]: threeDSecureCheck() Secure code NOT valid but with 3D Data");
					originalISOMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
					sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_WARNING, 
					FEP_RESPONSE_CODE_3D_FAILED, internalFormat);
					return hasError;
	            }
		            
		        // If DE 48.42 subfield 3 is 2, 3D value is not present
		        else if (secureCode.endsWith(UCAF_SUPPORTED_AND_PROVIDED_2) && !field48Elements.hasField(43)){
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
		                "[BanknetIsRequest]: threeDSecureCheck() NON-3D Merchant send 3D data");
		            originalISOMsg.set(39, FEP_BNK_EXTERNAL_CODE_NOT_PERMITTED_57);
		            sendDeclineMessage(fepMessage, originalISOMsg, LogOutputUtility.LOG_LEVEL_WARNING, 
					FEP_RESPONSE_CODE_3D_FAILED, internalFormat);
		            return hasError;
		        }	
			}// End of checking of unacceptable setting for 3D transactions
			
			return true;
				
		} catch (ISOException ie) {
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] execute(): End Process");
			return hasError;
		} catch (SharedMemoryException sme) {
			args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetIsRequest] execute(): End Process");
			return hasError;
		} catch (Exception e) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetIsRequest] execute(): End Process");
			return hasError;
		}
	}
	
	
	/**
	 * Start RM5850 Support for Account Status Inquiry ASICheckError
	 * 
	 * @param fepMessage
	 * @param originalISOMsg
	 * @return 
	 * 		true : is not an ASI transaction
	 * 		false : is an ASI transaction
	 * @author rsiaotong : 03/28/2014
	 */
	private boolean isNotAnASITransaction(FepMessage fepMessage, ISOMsg originalISOMsg) {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetIsRequest] : Start of ASICheck");
		try {
			// if ASICheckFormatError: true -> sendDeclineMessage
			if (hasIncompleteDataForASI(originalISOMsg)) {
				sendDeclineMessage(fepMessage, originalISOMsg,
						LogOutputUtility.LOG_LEVEL_ERROR,
						Constants.FEP_BNK_RESPONSE_DO_NOT_HONOR,
						internalFormat);
				
				
				return true;
			}

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] : End of ASICheck()");
			return false;
		} catch (ISOException ie) {
			args[0] = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,
					FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] execute(): End Process");
			return false;
		} catch (SharedMemoryException sme) {
			args[0] = sme.getMessage();
			sysMsgID = mcpProcess
					.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING,
							SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID,
					LogOutputUtility.LOG_LEVEL_INFORMATION,
					SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] execute(): End Process");
			return false;
		} catch (Exception e) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] execute(): End Process");
			return false;
		}
	}

	/**
	 * Start RM5850 Support for Account Status Inquiry This method checks
	 * required fields for Account Status Inquiry transaction.
	 * 
	 * @param originalISOMsg
	 * @return 
	 * 			false: meets required fields for ASI transaction
	 * 			true: did not meet required fields for ASI transaction
	 * @throws ISOException
	 * @author rsiaotong : 03/28/2014
	 */
	private boolean hasIncompleteDataForASI(ISOMsg originalISOMsg)
			throws ISOException {

		int field4; // DE4

		// DE18
		String field18_mcc = (originalISOMsg.hasField(18) ? originalISOMsg
				.getString(18) : Constants.NO_SPACE);

		// DE48
		ISOMsg field48 = (originalISOMsg.hasField(48) ? (ISOMsg) originalISOMsg
				.getValue(48) : null);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : hasIncompleteDataForASI() field48:  "
				+ field48);

		// DE124
		String field124 = (originalISOMsg.hasField(124)) ? originalISOMsg
				.getString(124) : NO_SPACE;

		// MTI check
		if (!(MTI_0100.equals(originalISOMsg.getMTI()) || MTI_0120
				.equals(originalISOMsg.getMTI()))) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] : ASICheck: MTI:NG");
			return true;
		}

		// DE4 Check
		if (originalISOMsg.hasField(4)) {
			field4 = Integer.parseInt(originalISOMsg.getString(4));
		} else {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] : ASICheck: DE4:Null");
			return true;
		}

		// Check existence of DE 3 [csampaga:9/4/2014]
		if (!originalISOMsg.hasField(3)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] : Is not a transaction message.");
			return true;
		} else {
			// isPurchaseASI
			if (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(originalISOMsg
					.getString(3).substring(0, 2))) {
				// if DE4 <> 0; return format error: true
				if (0 != field4) {
					mcpProcess
							.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
									"[BanknetIsRequest] : ASICheck: PurchaseASI - DE4:Error");
					return true;
				}

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetIsRequest] : ASICheck: PurchaseASI");
				return false;

				// DE3_1 == '28' Payment
			} else if (Constants.FLD3_1_TRANSACTION_TYPE_28.equals(originalISOMsg
					.getString(3).substring(0, 2))) {
				// DE4 Check
				// else; return format error: true
				if (!(field4 >= 0)) {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: DE4: Error");
					return true;
				}

				// Check if field 18 (MCC) is for MoneySend Payment transaction
				if (Constants.MONEYSENDPAYMENT_MCC_LIST.contains(field18_mcc)) {
					// DE124_1-4 Check
					// if has DE124, isMoneySendPaymentASI
					if (field124.equals(NO_SPACE)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isMoneySendPaymentASI: F124: Error");
						return true;
					}

					// DE48_77
					String field48Sub77 = (originalISOMsg.hasField(48)) ? field48
							.hasField(77) ? field48.getString(77) : NO_SPACE
							: NO_SPACE;
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest] : hasIncompleteDataForASI() field48_77:  "
							+ field48Sub77);

					// DE48_77 Check
					if (!NO_SPACE.equals(field48Sub77) && !Constants.MONEYSENDPAYMENT_TRXTYPE_LIST.contains(field48Sub77) 
							&& !SysCode.MTI_0120.equals(originalISOMsg.getMTI())) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isMoneySendPaymentASI: DE48_77: Error");
						return true;
					}

					// DE61_10
					String field61Sub10 = (originalISOMsg.hasField(61)) ? ((ISOMsg) originalISOMsg
							.getValue(61)).hasField(10) ? ((ISOMsg) originalISOMsg
							.getValue(61)).getString(10) : NO_SPACE : NO_SPACE;

					// DE61_10 Check
					if (!Constants.FLD61_10_NOT_A_CAT_TRANSACION_0
							.equals(field61Sub10)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isMoneySendPaymentASI: DE61_10: Error");
						return true;
					}

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isMoneySendPaymentASI");
					return false;
					// Else, Check if paymentASI
				} else {
					// DE48_77
					String field48Sub77 = (originalISOMsg.hasField(48)) ? field48
							.hasField(77) ? field48.getString(77) : NO_SPACE
							: NO_SPACE;

					// DE48_77 Check
					if (!Constants.PAYMENT_ASI_TRXTYPE_INDICATOR_LIST
							.contains(field48Sub77)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isPaymentASI: DE48_77: Error");
						return true;
					}

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetIsRequest] : ASICheck: isPaymentASI");
					return false;
				}
			}
		}
		return false;
	}
	
	/**
	 * Start RM5850 Reminder for MasterCard April 2014 Compliance - Global 555
	 * Payment Account Status Inquiry Transactions (2013 Enhancement) 
	 * Checks required field for Accoung Status Inquiry Transaction
	 *  
	 * @return true : false
	 * @author rsiaotong : 20140429
	 */
	private boolean isASITransaction(ISOMsg isoMsg) throws ISOException {
		// DE61
		ISOMsg field61_posData = (isoMsg.hasField(61) ? (ISOMsg) isoMsg
				.getValue(61) : null);

		// DE61_7
		String field61Sub7 = (isoMsg.hasField(61)) ? (field61_posData)
				.hasField(7) ? (field61_posData).getString(7) : NO_SPACE
				: NO_SPACE;

		// DE61_7 Check
		if (Constants.FLD61_7_POS_TRANSACTION_STATUS_8.equals(field61Sub7)) {
			return true;
		}
		return false;
	}
	
	/**
	 * [ACSS)EOtayde 12182014: RM 5838: [Mastercard Enhancement] - MoneySend Enhancements
	 * 		- Implemented in ACSM and replicated in ACSA]
	 * 
	 * This method returns if the message received for moneysend paymen transaction did not
	 * meet the require fields.
	 * 
	 * @throws ISOException
	 * @param originalISOMsg
	 * @return true : moneysend payment transaction did not meet the required
	 *         fields false : moneysend payment transaction meets the required
	 *         fields
	 * @author vaspiras : 04/8/2014
	 */
	private boolean isNotAMoneySendPaymentTransaction(FepMessage fepMessage,
			ISOMsg originalISOMsg) {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetIsRequest] : Start of isNotAMoneySendPaymentTransaction()");
		try {
			if (hasIncompleteDataForMoneySendPayment(originalISOMsg)) {
				sendDeclineMessage(fepMessage, originalISOMsg,
						LogOutputUtility.LOG_LEVEL_ERROR,
						Constants.FEP_BNK_RESPONSE_DO_NOT_HONOR,
						internalFormat);
				return true;
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] : End of isNotAMoneySendPaymentTransaction()");
			return false;
		} catch (Exception e) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsRequest] execute(): End Process");
			return false;
		}
	}
	
	/**
	 * [ACSS)EOtayde 12182014: RM 5838: [Mastercard Enhancement] - MoneySend Enhancements
	 * 		- Implemented in ACSM and replicated in ACSA]
	 * 
	 * This method validates each field required for MoneySend Payment transaction
	 * 
	 * @throws ISOException
	 * @param originalISOMsg
	 * @return true : did not meet required fields for moneysend payment
	 *         transaction false : meets the required fields for moneysend
	 *         payment transaction
	 */
	private boolean hasIncompleteDataForMoneySendPayment(ISOMsg originalISOMsg)
			throws ISOException {
		String field18_mcc = (originalISOMsg.hasField(18) ? originalISOMsg
				.getString(18) : Constants.NO_SPACE);

		ISOMsg field48 = (originalISOMsg.hasField(48) ? (ISOMsg) originalISOMsg
				.getValue(48) : null);

		String field48Sub77 = (originalISOMsg.hasField(48)) ? field48
				.hasField(77) ? field48.getString(77) : NO_SPACE : NO_SPACE;
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetIsRequest] : hasIncompleteDataForMoneySendPayment(): DE 48 SE 77: " + field48Sub77);

		// DE61
		ISOMsg field61_posData = (originalISOMsg.hasField(61) ? (ISOMsg) originalISOMsg
				.getValue(61) : null);

		String field61Sub10 = (originalISOMsg.hasField(61)) ? (field61_posData)
				.hasField(10) ? (field61_posData).getString(10) : NO_SPACE
				: NO_SPACE;

		String field124 = (originalISOMsg.hasField(124)) ? originalISOMsg
				.getString(124) : NO_SPACE;

		// Check existence of DE 3 [csampaga:9/4/2014]
		if (!originalISOMsg.hasField(3)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
					"[BanknetIsRequest] : Is not a transaction message.");
			return true;
		} else {
			if (Constants.FLD3_1_TRANSACTION_TYPE_28.equals(originalISOMsg
					.getString(3).substring(0, 2))) {

				if (Constants.MONEYSENDPAYMENT_MCC_LIST.contains(field18_mcc)) {
					// Field 48.77 is not mandatory. Check the value if exists
					if (!NO_SPACE.equals(field48Sub77) && !Constants.MONEYSENDPAYMENT_TRXTYPE_LIST
							.contains(field48Sub77) && !SysCode.MTI_0120.equals(originalISOMsg.getMTI())) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
							"[BanknetIsRequest] : MoneySendPaymentCheck: DE48_77: Error");
						return true;
					}

					if (!Constants.MONEYSENDPAYMENT_CAT_LIST.contains(field61Sub10)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
							"[BanknetIsRequest] : MoneySendPaymentCheck: DE61_10: Error");
						return true;
					}

					if (NO_SPACE.equals(field124)) {
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
							"[BanknetIsRequest] : MoneySendPaymentCheck: F124: Error");
						return true;
					}
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetIsRequest] : MoneySendPaymentCheck: isMoneySendPayment");
				}
				return false;
			}
		}
		return false;
	}
	
	//[START] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
	private void unsetSubField48(ISOMsg replyISOMsg){
		int field48UnsetList[] = { 20, 32, 33, 47, 58, 74, 77 };
		try {
			ISOMsg field48Component = (ISOMsg) replyISOMsg.getValue(48);
			field48Component.unset(field48UnsetList);
		} catch (ISOException e) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsRequest]:  Unable to unset subfield of Field 48");
		}
	}
	//[END] JAnastacio 20150611 RM#6164 Some sub-fields of F48 should not be included in 0110 and 0410 response
}// End of class
