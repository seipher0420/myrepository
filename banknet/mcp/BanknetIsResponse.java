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
 */

package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.aeoncredit.fep.common.SysCode.MTI_0190;
import static com.aeoncredit.fep.common.SysCode.MTI_0420;
import static com.aeoncredit.fep.common.SysCode.MTI_0400;
import static com.aeoncredit.fep.common.SysCode.MTI_0120;
import static com.aeoncredit.fep.common.SysCode.MTI_0100;
import static com.aeoncredit.fep.common.SysCode.LN_BNK_LCP;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_ORG_MESSAGE;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_SAF_REQ;
import static com.aeoncredit.fep.common.SysCode.QH_DFI_FEP_MESSAGE;
import static com.aeoncredit.fep.common.SysCode.QH_PTI_MCP_RES;
import static com.aeoncredit.fep.common.SysCode.NETWORK_BKN;
import static com.aeoncredit.fep.common.SysCode.LN_MON_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_ACE_STORE;
import static com.aeoncredit.fep.common.SysCode.LN_HOST_STORE;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;
import static com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.*;

import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.ATMCashAdvanceFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSHKFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.IC_INFORMATION;
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

public class BanknetIsResponse extends BanknetCommon {
	
	/** contains the internal format reply from FEP */
	private FepMessage parameterMessage;

	/** The ISOMsg request, it contains the original fields */
	private FepMessage sharedMemoryMessage;

	/** dao */
	private FEPT022BnkisDAO dao;
	
	/** transaction manager */
	private FEPTransactionMgr transactionManager;

	/** table domain */
	private FEPT022BnkisDomain domain;
	
	/** Process of creating Internal Format**/
	private BanknetInternalFormatProcess inFormatProcess;
	
	/** Variables used for Exception Handling*/
	private String[] args   = new String[2];
	private String sysMsgID  = NO_SPACE;
	
	/**
	 * Constructor.
	 * @param mcpProcess instance of IMCPProcess from the message dispatcher
	 */
	public BanknetIsResponse(IMCPProcess mcpProcess) {
		super(mcpProcess);

		try {
			transactionManager = mcpProcess.getDBTransactionMgr();
			dao = new FEPT022BnkisDAO(transactionManager);
			domain = new FEPT022BnkisDomain();
		} catch (DBNOTInitializedException dnie) {
			args[0]  = dnie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(dnie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: End Process");
			return;
		}// End of try-catch
	}// End of constructor

	/**
	* Business logic of the class. FepMessage has as its messageContents an
	* InternalFormat and translates it to ISOMsg
	* @param fepMessage The Fep Message recieved
	*/
	public void execute(FepMessage message) {
		ISOMsg isoMessageContent;
		InternalFormat internalContent;
		String transactionType;
		String pinCheckResult;
		String authJudge;
		
		// Store the parameter message to allow editMsg() to use it
		parameterMessage = message; 
		internalContent = parameterMessage.getMessageContent();
		
		// [mqueja: 06-23-2011] Added for Logging Purposes
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "received a message from <"+ 
				     mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
				"[Process type:" + message.getProcessingTypeIdentifier() + 
				"| Data format:" + message.getDataFormatIdentifier() +
				"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + message.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalContent.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| Auth ID Response from HOST:" + internalContent.getValue(
								HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE).getValue() + 
				"| SourceID:" + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| DestinationId:" + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() + 
				"| FEP response Code:" + internalContent.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
   	 	try{
   	 		//[msayaboc: 07-01-2011] Add time stamp to internal message. This is used for MON(TRNHISTBLE).
   	 		internalContent.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), PROCESS_I_O_TYPE_ISRES);
	 	 } catch (SharedMemoryException sme) {
	 		args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
			return;
		 } // End of try catch
		
		/*
		 * Get the ISOMsg from the shared memory. 
		 *  Call super.getTransactionMessage() method. 
		 *  If return is null, end process
		 */
		sharedMemoryMessage = getTransactionMessage(parameterMessage.getFEPSeq());
		// [MAguila: 20120619] Redmine #3860 Updated Logging
		if (null == sharedMemoryMessage) {
	 		 args[0] = parameterMessage.getFEPSeq();
	 		 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_SYSLOG_INFO_2015);
	 		 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_SYSLOG_INFO_2015, args);         
	 		 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	 				 "[BanknetIsResponse] : sharedFepMessage is null. Return from execute()");
	 		 return;
		}// End of if

		/*
		 * Get the LogSequence from FepMessage
		 * Set the LogSequence
		 * Call mcpProcess.setLogSequence(message.getLogSequence()) to set the message into mcpProcess
		 */
		mcpProcess.setLogSequence(sharedMemoryMessage.getLogSequence());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,  "[BanknetIsResponse]: Logsequence: " + 
				sharedMemoryMessage.getLogSequence());
		
		ISOMsg isoMsg = sharedMemoryMessage.getMessageContent();
		
		/*
		 * [mqueja:14/06/2011] [Redmine#2194] 
		 * [Create reversal advice when FEP is down and the transaction is approved by HOST]
		 */
		int lineStatus = getLineStatus();
		
		if(SysCode.LINE_NG == lineStatus){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_WARNING, "BANKNET[IsResponse]: Line Status is disconnected");
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Response Code:" 
					+ internalContent.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue());
			
			if(internalContent.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue().equals(
					SysCode.FEP_RESPONSE_CODE_OK)){
				ISOMsg reversalIsoMsg = null;
				String queueName = null;
				
				editDecline(isoMsg);
				
				// Insert to table 0110 (internal format)
				try {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: inserting to log table");
					insertToLogTable(isoMsg, true, COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_APPROVE);

				} catch (Exception e) {
					args[0]  = e.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
				}
				
				// Create 0420 ISOMsg
				try {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetIsResponse]: creating Reversal IsoMsg from InternalFormat");
					reversalIsoMsg = createReversalAdvice(sharedMemoryMessage);
					
				} catch (IllegalParamException ipe) {
					args[0]  = ipe.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
				
				} catch (SharedMemoryException sme) {
					args[0]  = sme.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
					
				} catch (AeonDBException ade) {
					args[0]  = ade.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ade));
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
					
				} catch (ISOException ie) {
					args[0]  = ie.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
				}// End of try catch
				
				// Create and set new fep sequence number for 0420 Message
				try {
					String msgProcessingNum = mcpProcess.getSequence(SysCode.FEP_SEQUENCE);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: msgProcessingNo: " + msgProcessingNum);
					parameterMessage.setFEPSeq(msgProcessingNum);
					
				} catch (AeonDBException ade) {
					args[0]  = ade.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ade));
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
					
				} catch (IllegalParamException ipe) {
					args[0]  = ipe.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
				}
				
				// Create 0420 InternalFormat from 0420 ISOMsg
				inFormatProcess = new BanknetInternalFormatProcess(mcpProcess);
				InternalFormat reversalInternalFormat = inFormatProcess.createInternalFormatFromISOMsg(reversalIsoMsg);				
				
				// [mqueja:09/07/2011] [added FepResponseCode and OrigMsgProcNum for Timeout trx]
				reversalInternalFormat.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_FEP_TIMED_OUT);
				reversalInternalFormat.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME, reversalIsoMsg.getString(90).substring(10, 20));
				reversalInternalFormat.setValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER, sharedMemoryMessage.getFEPSeq());
								
				if(null == reversalInternalFormat || SPACE.equals(reversalInternalFormat)){
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: reversalInternalFormat is null");
				}
				/**
				 * [msayaboc 07-01-11] Added important fields including timestamp
				 */	
				try {					
					String timeStamp           = mcpProcess.getSystime();
					final String processIOType = PROCESS_I_O_TYPE_ISRES;
					final String processNo     = mcpProcess.getProcessName();
					
					reversalInternalFormat.addTimestamp(processNo, timeStamp, processIOType);
					
				} catch (SharedMemoryException sme) {
					args[0]  = sme.getMessage();
					sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
					mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
					return;
				} // End of try catch
				
				parameterMessage.setMessageContent(reversalInternalFormat);
				parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
				parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
				
				// Send to queues
				queueName = mcpProcess.getQueueName(LN_MON_STORE);
				sendMessage(queueName, parameterMessage);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ queueName + ">" +
						"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + parameterMessage.getDataFormatIdentifier() +
						"| MTI:" + reversalInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + parameterMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + reversalInternalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + reversalInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + reversalInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ reversalInternalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				
				queueName = mcpProcess.getQueueName(LN_HOST_STORE);
				sendMessage(queueName, parameterMessage);
				// [MAguila: 20120620] Redmine 3876 Added logging for Original FepSeq
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ queueName + ">" +
						"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + parameterMessage.getDataFormatIdentifier() +
						"| MTI:" + reversalInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + parameterMessage.getFEPSeq() +
						"| Original fepSeq: " + reversalInternalFormat.getValue(HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER).getValue() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + reversalInternalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + reversalInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + reversalInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ reversalInternalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				
				queueName = mcpProcess.getQueueName(LN_ACE_STORE);
				sendMessage(queueName, parameterMessage);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
						+ queueName + ">" +
						"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
						"| Data format:" + parameterMessage.getDataFormatIdentifier() +
						"| MTI:" + reversalInternalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + parameterMessage.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + reversalInternalFormat.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + reversalInternalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + reversalInternalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ reversalInternalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				
			}// End of IF
			return;
		}// End of if(SysCode.LINE_NG == lineStatus)

		/*
		 * [MAguila: 09/30/2011 Based on Email Conversation by Raffy and Maebayashi]
		 *   1. When brandMCP/EDCMCP receives 13035 of FEP Response code (Pickup judged by FEP system)
		 *      if it's ATM transaction:  No need to change logic.
		 *      if it's EDC transaction:  Set 13034 as FEP Response code and converts it to external response code 
		 *                                so that operators can see it in MON screen.
		 *   2. When brandMCP/EDCMCP receives 14003 of FEP Response code (Pickup judged by HOST)
		 *      if it's ATM transaction:  No need to change logic.
		 *      if it's EDC transaction:  Set 14030 as FEP Response code and converts it to external response code 
		 *                                so that operators can see it in MON screen.
		 */
		String transType = SPACE;
		String fepRespCode = SPACE;
		if (null != isoMsg && null != internalContent) {
			transType = isoMsg.getString(3).substring(0, 2);
			fepRespCode = internalContent.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue();

			if (null != transType && null != fepRespCode && FLD3_1_TRANSACTION_TYPE_00.equals(transType)) {
				if (FEP_RESPONSE_CODE_PICK_UP_FEP.equals(fepRespCode)) {
					internalContent.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_HOLD_FEP);
				} else if (FEP_RESPONSE_CODE_PICK_UP_HOST.equals(fepRespCode)) {
					internalContent.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_HOLD_HOST);
				}
			}
		}
		
		// Edit the ISOMsg according to the InternalFormat message.
		try {
			editMsg(sharedMemoryMessage);
		} catch (Exception e) {
			handleEditMsgError(e);
			args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
			return;
		}

		// Update the BANKNET Issuing Log Table.
		try {
			isoMessageContent = (ISOMsg) sharedMemoryMessage.getMessageContent();
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetIsResponse]: Field 39: " + isoMessageContent.getString(39));
			
			if (FLD39_RESPONSE_CODE_SUCCESS.equals(isoMessageContent.getString(39))) {
				insertToLogTable(isoMessageContent, true,  COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_APPROVE);
				
			} else {
				insertToLogTable(isoMessageContent, true, COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_DECLINE);
			}
			
		} catch (Exception e) {			
			try {
				transactionManager.rollback();
				
			} catch (SQLException e1) {
				args[0]  = e.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
				return;
			}
			
			args[0]  = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] execute(): End Process");
			return;
		}
		
//		/*
//		 * Output the transaction history log (MON STORE process) 
//		 * Send to MON STORE process
//		 */
//		parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
//		parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
//		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, parameterMessage.getLogSequence());
//		sendMessage(mcpProcess.getQueueName(LN_MON_STORE), parameterMessage);
//		
//		// [mqueja: 06-23-2011] Added for Logging Purposes
//		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
//				+ mcpProcess.getQueueName(LN_MON_STORE) + ">" +
//				"[Process type:" + message.getProcessingTypeIdentifier() + 
//				"| Data format:" + message.getDataFormatIdentifier() +
//				"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
//				"| FepSeq:" + message.getFEPSeq() +
//				"| NetworkID: BNK" + 
//				"| Transaction Code(service):" + internalContent.getValue(
//						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
//				"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
//				"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
//				"| FEP Response Code: "+ internalContent.getValue(
//						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
//		
//
//		/*
//		 * Output the to ACE SAF if transaction is declined. 
//		 * Send to ACE STORE process
//		 * [mqueja: 12/4/2011] [Send to ACE SAF if transaction is declined]
//		 * [mqueja: 01/17/2012] [REMOVED Send to ACE SAF if transaction is declined]
//		 */
//		if(!FLD39_RESPONSE_CODE_SUCCESS.equals(isoMessageContent.getString(39))) {
//			sendMessage(mcpProcess.getQueueName(LN_ACE_STORE), parameterMessage);
//			
//			// [mqueja: 06-23-2011] Added for Logging Purposes
//			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"
//					+ mcpProcess.getQueueName(LN_ACE_STORE) + ">" +
//					"[Process type:" + message.getProcessingTypeIdentifier() +
//					"| Data format:" + message.getDataFormatIdentifier() +
//					"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
//					"| FepSeq:" + message.getFEPSeq() +
//					"| NetworkID: BNK" +
//					"| Transaction Code(service):" + internalContent.getValue(
//							HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() +
//					"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
//					"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
//					"| FEP Response Code: "+ internalContent.getValue(
//							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
//		}
		
		/*
		 * If Transaction type of mapControl_info_part is "02" or "04"
		 * If PIN CHECK RESULT of mapHost_FEP_Add_info is NOT "0"
		 * If AUTHORIZATION JUDGEMENT DIVISION of mapHost_header is "1"
		 *   Send to HOST STORE process 
		 */
		transactionType = internalContent.getValue(CONTROL_INFORMATION.TRANSACTION_TYPE).getValue();
		pinCheckResult = internalContent.getValue(HOST_FEP_ADDITIONAL_INFO.PIN_CHECK_RESULT).getValue();
		authJudge = internalContent.getValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION).getValue();

		/*
		 * [mqueja:07/08/2011] [added checking for pinCheckResult and authJudge
		 */
	   	 if (null==transactionType) {
			 transactionType = NO_SPACE;
		 }
		 if ((null == pinCheckResult) || ("").equals(pinCheckResult.trim())) {
			 pinCheckResult = PIN_CHECK_RESULT_0;
		 }
		 if ((null == authJudge) || ("").equals(authJudge.trim())) {
			 authJudge = NO_SPACE;
		 }
		 


//		sendMessage(mcpProcess.getQueueName(LN_BNK_LCP),
//				sharedMemoryMessage);
//		
//		try {
//			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"+
//					mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
//					"[Process type:" + message.getProcessingTypeIdentifier() +
//					"| Data format:" + message.getDataFormatIdentifier() +
//					"| MTI:" + isoMessageContent.getMTI() +
//					"| fepSeq:" + message.getFEPSeq() +
//					"| NetworkID: BNK" +
//					"| Processing Code(first 2 digits):" + isoMessageContent.getString(3).substring(0,2) +
//					"| Response Code: " + (
//							(isoMessageContent.hasField(39)) ? isoMessageContent.getString(39) : "Not Present" + "]"));
//		} catch (ISOException ie) {
//			args[0]  = ie.getMessage();
//			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
//			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
//			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
//			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
//			return;
//		}
		
		/*
		 * 04222015 ACSS)MLim START >> Sending to Master Card if Standin Handling
		 * if the message does NOT come from auth OR the response code does not start in 15XXX
		 * and the stand in flag is NOT do not send to stand in... --> send to BrandLCP.
		 * Otherwise, do not send.
		 */
//		if ((!(authJudge.equals(AUTH_JUDGMENT_DIVISION_1)) 
//				|| !internalContent.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue().startsWith(Constants.STAND_IN_RESP_CODE_START))
//					&& !getSendStandinResponseFlag().equals(Constants.DO_NOT_SEND_STANDIN)){ 
//			
//			sendMessage(mcpProcess.getQueueName(LN_BNK_LCP),
//					sharedMemoryMessage);
			
			if ((authJudge.equals(AUTH_JUDGMENT_DIVISION_1)
					|| internalContent.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue().startsWith(Constants.STAND_IN_RESP_CODE_START))
					&& getSendStandinResponseFlag().equals(Constants.DO_NOT_SEND_STANDIN)){
			
			try {
//				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"+
//						mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				
				//[rrababaya 20150807] RM#6362 [MasterCard] Unset the DE39 in Authorization Message for FEP Stand-in : Start
				//internalContent.setValue(InternalFieldKey.HOST_COMMON_DATA.RESPONSE_CODE, "85");
				//isoMessageContent.set(39, "85");
				//[rrabaya 20150807] RM#6362 [MasterCard] Unset the DE39 in Authorization Message for FEP Stand-in : End
				
				internalContent.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, "12085");
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"Message not sent to <"+
						mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + "> due to Stand-In! " +
						"[Process type:" + message.getProcessingTypeIdentifier() +
						"| Data format:" + message.getDataFormatIdentifier() +
						"| MTI:" + isoMessageContent.getMTI() +
						"| fepSeq:" + message.getFEPSeq() +
						"| NetworkID: BNK" +
						"| Processing Code(first 2 digits):" + isoMessageContent.getString(3).substring(0,2));
				
				/*
				 * Output the transaction history log (MON STORE process) 
				 * Send to MON STORE process
				 */
				parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
				parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, parameterMessage.getLogSequence());
				sendMessage(mcpProcess.getQueueName(LN_MON_STORE), parameterMessage);
				
				// [mqueja: 06-23-2011] Added for Logging Purposes
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
						+ mcpProcess.getQueueName(LN_MON_STORE) + ">" +
						"[Process type:" + message.getProcessingTypeIdentifier() + 
						"| Data format:" + message.getDataFormatIdentifier() +
						"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + message.getFEPSeq() +
						"| NetworkID: BNK" + 
						"| Transaction Code(service):" + internalContent.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
						"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalContent.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
				
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
				return;
			}
			
		} else {
			/*
			 * Output the transaction history log (MON STORE process) 
			 * Send to MON STORE process
			 */
			parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
			parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, parameterMessage.getLogSequence());
			sendMessage(mcpProcess.getQueueName(LN_MON_STORE), parameterMessage);
			
			// [mqueja: 06-23-2011] Added for Logging Purposes
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
					+ mcpProcess.getQueueName(LN_MON_STORE) + ">" +
					"[Process type:" + message.getProcessingTypeIdentifier() + 
					"| Data format:" + message.getDataFormatIdentifier() +
					"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + message.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalContent.getValue(
							HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalContent.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			

			/*
			 * Output the to ACE SAF if transaction is declined. 
			 * Send to ACE STORE process
			 * [mqueja: 12/4/2011] [Send to ACE SAF if transaction is declined]
			 * [mqueja: 01/17/2012] [REMOVED Send to ACE SAF if transaction is declined]
			 */
			if(!FLD39_RESPONSE_CODE_SUCCESS.equals(isoMessageContent.getString(39))) {
				sendMessage(mcpProcess.getQueueName(LN_ACE_STORE), parameterMessage);
				
				// [mqueja: 06-23-2011] Added for Logging Purposes
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"
						+ mcpProcess.getQueueName(LN_ACE_STORE) + ">" +
						"[Process type:" + message.getProcessingTypeIdentifier() +
						"| Data format:" + message.getDataFormatIdentifier() +
						"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
						"| FepSeq:" + message.getFEPSeq() +
						"| NetworkID: BNK" +
						"| Transaction Code(service):" + internalContent.getValue(
								HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() +
						"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
						"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
						"| FEP Response Code: "+ internalContent.getValue(
								CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			}
			
			/*
			 * [lquirona:08/04/2010] [Redmine#774] [The message is being sent to HOST_STORE 
			 *  because PIN_CHECK_RESULT is empty]
			 *  
			 * [mqueja:08/25/2011] [removed checking of pinCheckResult, for FEPMCP also do its checking and also sends to HOST Store.]
			 * 		 * [mqueja:06/13/2012] [Redmine#3881] [Sending of FEP or ACE Decline Transactions to HOST Saf]
			 * [MAguila: 07/17/2012] [Related to Redmine #3881: Removed checking if Stand-in or Fep/ACE Declined Transactios since
			 *  FEP already sends messages to Host SAF for such cases]
			 *  [FSambrano: 10/11/2012 ] [Removed redmine 3881. Not applicable for HK]
			 */
			 if(transactionType.equals(PREFIX_TRANS_CODE_AUTHORIZATION_ADVICE) ||
					 transactionType.equals(PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE) ||
	    			 (authJudge.equals(AUTH_JUDGMENT_DIVISION_1))) {
				 sendMessage(mcpProcess.getQueueName(LN_HOST_STORE), parameterMessage);
					// [mqueja: 06-23-2011] Added for Logging Purposes
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
							+ mcpProcess.getQueueName(LN_HOST_STORE) + ">" +
							"[Process type:" + message.getProcessingTypeIdentifier() + 
							"| Data format:" + message.getDataFormatIdentifier() +
							"| MTI:" + internalContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
							"| FepSeq:" + message.getFEPSeq() +
							"| NetworkID: BNK" + 
							"| Transaction Code(service):" + internalContent.getValue(
									HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
							"| SourceID: " + internalContent.getValue(HOST_HEADER.SOURCE_ID).getValue() +
							"| Destination ID: " + internalContent.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
							"| FEP Response Code: "+ internalContent.getValue(
									CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			}
		

			/*
			 * Encode the ISOMsg 
			 * Transmit the Response Message
			 */
			isoMessageContent = (ISOMsg) sharedMemoryMessage.getMessageContent();
			isoMessageContent.setPackager(packager);
				
		    byte[] messageArray = pack(isoMsg);
		        
		    // If error occurs,
		    if(messageArray.length < 1) {
		    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse]Error in Packing Message");
		    mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] execute(): End Process");
		                    return;
		    } // End of if bytesMessage is 0        
		        
		    sharedMemoryMessage.setMessageContent(messageArray);
			sharedMemoryMessage.setProcessingTypeIdentifier(QH_PTI_MCP_RES);
			sharedMemoryMessage.setDataFormatIdentifier(QH_DFI_ORG_MESSAGE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Logsequence: " 
				+ sharedMemoryMessage.getLogSequence());
				
			
			try {
				
				sendMessage(mcpProcess.getQueueName(LN_BNK_LCP),
						sharedMemoryMessage);
				String queueName = mcpProcess.getQueueName(SysCode.LN_BNK_LCP);

				
//				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"Message not sent to <"+
//						queueName + "> due to Stand-In! " +
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <"+
						queueName + ">" +
						"[Process type:" + message.getProcessingTypeIdentifier() +
						"| Data format:" + message.getDataFormatIdentifier() +
						"| MTI:" + isoMessageContent.getMTI() +
						"| fepSeq:" + message.getFEPSeq() +
						"| NetworkID: BNK" +
						"| Processing Code(first 2 digits):" + isoMessageContent.getString(3).substring(0,2) +
						"| Response Code: " + (
								(isoMessageContent.hasField(39)) ? isoMessageContent.getString(39) : "Not Present" + "]"));
			} catch (ISOException ie) {
				args[0]  = ie.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
				return;
			}
		}
	
		/*
		 * 04222015 ACSS)MLim END << Sending to Master Card if Standin Handlin 
		 * 
		 */
		

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute() end");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
			 "********** BRAND MCP ISSUING RESPONSE END PROCESS (Seq="+ message.getLogSequence() + ")**********");
	}// End of execute()






	/**
	 * This method updates the contents of the FepMessage parameter. 
	 * The parameter is the message from shared memory, which contains an ISOMsg 
	 * The execute() parameter is also used to get values from InternalFormat
	 * @param message The Fep Message received
	 * @throws ISOException
	 * @throws Exception
	 */
	private FepMessage editMsg(FepMessage message) throws ISOException, Exception {
		List<String> CVV_RESULT_DECLINE = 
			Arrays.asList(FLD48_87_CVV_RESULT_DECLINE_01, FLD48_87_CVV_RESULT_DECLINE_02, 
					FLD48_87_CVV_RESULT_DECLINE_03, FLD48_87_CVV_RESULT_DECLINE_04,
					FLD48_87_CVV_RESULT_DECLINE_99);
		
		int mti0100Fields[] = {12, 13, 14, 18, 22, 26, 35, 42, 43, 44, 
				45, 52, 53, 54, 60, 102, 103, 112, 121, 123, 124, 125};
		
		int mti0120Fields[] = {38, 48, 55, 95, 120, 127};
		
		int mti0400Fields[] = {23, 38, 55, 120, 127};
		
		int mti0420Fields[] = {23, 38, 48, 55, 120};
		
		String messageType;
		ISOMsg isoMsg;
		InternalFormat internalFormat;
		String cvvIndicator;
		String cvvResult;
		ISOMsg field48Component;
		String fepRespCode = null;
		String standInResult = null;
		String externalRespCode = null;
		String authIdResponse;
		//[rrabaya:11/27/2014]RM#5850 Unset DE 48.77
		int field48Mask[] = {20, 32, 33, 47, 58, 74, 77};

		isoMsg = (ISOMsg) message.getMessageContent();
		internalFormat = (InternalFormat) parameterMessage.getMessageContent();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] editMsg() start");
		
		// Check the MTI first
		messageType = isoMsg.getMTI();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: mti " + messageType);

		// Common fields to update
		//isoMsg.unset(mti0100Fields);
		isoMsg = unsetFields(isoMsg);
		
		// DE 39 Response Code
		fepRespCode = internalFormat.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue();
		externalRespCode = changeToExternalCode(NETWORK_BKN, fepRespCode);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: FEP response: " + fepRespCode);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Response Code: " + externalRespCode);
		
		//[rrabaya:11/27/2014]RM#5850 Reminder for MasterCard April 2014 Compliance
		if(isASITransaction(isoMsg) && isInvalidExtRespCodeForASI(externalRespCode)){
			fepRespCode = Constants.FEP_RESPONSE_CODE_DO_NOT_HONOR_ASI;
			externalRespCode = changeToExternalCode(NETWORK_BKN, fepRespCode);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: [Modified_ASI] FEP response: " + fepRespCode);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: [Modified_ASI] Response Code: " + externalRespCode);
		}
		
		if((null == externalRespCode) || (("").equals(externalRespCode.trim()))){ 
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
			"[BanknetIsResponse] No external response code found, set value of response code to 05 ");
			isoMsg.set(39, EXTERNAL_RESPONSE_CODE_DO_NOT_HONOR);
		}
		// If external response code
		else{
			isoMsg.set(39, changeToExternalCode(NETWORK_BKN, fepRespCode));
		}
		
		// Decide what to do depending on the MTI
		if (MTI_0100.equals(messageType)) {
	
			// DE 23 Card Sequence Number
			isoMsg.unset(23);

			/*
			 * DE 38 Authorization ID Response
			 * [mqueja:08/08/2011] [Redmine #3250] [set f38 to BitOff if DECLIN]
			 */ 
			authIdResponse = internalFormat.getValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: authIdResponse: " + authIdResponse);
			if ((null != authIdResponse) && !(SPACE.equals(authIdResponse)) 
					&& !(FLD38_DECLINE_CODE_DECLIN.equals(authIdResponse))){
				isoMsg.set(38, authIdResponse);
			} else {
				isoMsg.unset(38);
			}
			
			// DE 44 Additional Response Data 
			// [salvaro:20111025] Added Field 44 for HK (Redmine#3383)
			String addRespData = internalFormat.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_RESPONSE_DATA).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: addRespData: " + addRespData);
			
			if(mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
				if(null != addRespData && addRespData.trim().length() > 0){
					isoMsg.set(44, addRespData.trim());
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Field 44: " + addRespData);
				}// End of inner if statement
			} // End of if Country code is Hong Kong
			

			// DE 48 Additional Data (Redmine #488)
			cvvIndicator = internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_INDICATOR).getValue();
			cvvResult = internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_CHECK_RESULT).getValue();
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: cvvIndicator: " + cvvIndicator);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: cvvResult: " + cvvResult);
			
			//[ccalanog:10/07/2011] Redmine#3383 get stand-in result for DE48.87
			standInResult = internalFormat.getValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT).getValue();
			
			/*
			 * Approve: 00
			 * Decline: 01, 02, 03, 04, 99
			 * [nvillarete] added comparison if cvvResult != null'
			 */
			if (isoMsg.hasField(48)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Has field 48");
				String additionalData = internalFormat.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_DATA).getValue();
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: additionalData: " + additionalData);
				field48Component = (ISOMsg) isoMsg.getValue(48);
				// [ccalanog:10/07/2011] Redmine#3383
				// [salvaro:20111025] Edited handling of Field 48 for Redmine#3383
				if(mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
					if (null != standInResult && !"".equals(standInResult.trim())) { //if has stand-in result
						field48Component.set(87, FLD48_87_VALUE_NOT_PROCESSED); //CVV not processed
					} else if(null != additionalData && additionalData.trim().length() > 0) { // message processed by HOST
							StringBuilder addData = new StringBuilder(additionalData);
							addData.delete(0, 3);
							additionalData = ISOUtil.hexString(ISOUtil.asciiToEbcdic(addData.toString()));
							//[rrabaya 20150904] RM#6054 Remove the unecessary characters in field48
							isoMsg.set(48, ISOUtil.hex2byte(additionalData.trim()));
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Field 48: " + isoMsg.getValue(48));
					} // End of inner if statement
				} else {
					
					// [mqueja:10/05/2011] [moved cvvResult != null to inner if]
					if (cvvResult != null) {
						// [nvillarete] changed comparison '==' to '.equals()'
						if ((cvvIndicator.equals(FLD48_87_CVV_INDICATOR_1)) 
								&& (CVV_RESULT_DECLINE.contains(cvvResult))) {
							field48Component.set(87, FLD48_87_VALUE_INVALID);
							
						} else if ((cvvIndicator.equals(FLD48_87_CVV_INDICATOR_2)) 
								&& (cvvResult.equals(FLD48_87_CVV_RESULT_APPROVE_00))) {
							field48Component.set(87, FLD48_87_VALUE_CVV2_MATCH);
							
						} else if ((cvvIndicator.equals(FLD48_87_CVV_INDICATOR_2)) 
								&& (CVV_RESULT_DECLINE.contains(cvvResult))) {
							field48Component.set(87, FLD48_87_VALUE_CVV2_NO_MATCH);
							
						} 
			
						// Field 48 Unset Fields: (0100)
						field48Component.unset(field48Mask);
						
						// [mqueja:10/03/2011] [unsetting of 48.42 if value is not 213]
						if(field48Component.hasField(42)){
							if(!field48Component.getString(42).endsWith(FLD48_42_ECOMMERCE_INDICATOR_213)){
								field48Component.unset(42);
							}
						}
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Field 48: " + field48Component);
					}
					
					// [lquirona 20110830 - For AVS (48.82 = 51 || 52), set 48.83
					if(field48Component.hasField(82)){
						String AVSRequestIndicator = NO_SPACE;
						AVSRequestIndicator = field48Component.getString(82);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: AVS Request Indicator: " + AVSRequestIndicator);
						if(FLD48_82_AVS_REQUEST_INDICATOR_AVS_ONLY.equals(AVSRequestIndicator)
								|| FLD48_82_AVS_REQUEST_INDICATOR_AVS_AND_AUTH.equals(AVSRequestIndicator)){
							field48Component.set(83, FLD48_83_AVS_NOT_SUPPORTED);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Field 48.83: " + field48Component.getString(83));
						}
					}// End of AVS Request Handling
				}
			} else {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: No field 48");
			}// End of if-else (isoMsg.hasField(48)) 
			
			// [lquirona:7/14/2011] Update getting of ic response data
			String icFlag = internalFormat.getValue(CONTROL_INFORMATION.IC_FLAG).getValue();
			if(Constants.CONTROL_INFORMATION_IC_FLAG_1.equals(icFlag) && (null!=icFlag)){
				// - get value from IC_RESPONSE_DATA since we are creating tlv
				// response tags for Redmine Issue #2135 
				String icData = internalFormat.getValue(
						IC_INFORMATION.IC_RESPONSE_DATA).getValue();
	            
	           // [lquirona:7/13/2011] [Redmine #2943 and other applicable issues] Unset f55 for declined transactions
				if((null == icData) || ((NO_SPACE).equals(icData.trim()))){
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetIsResponse]: No icResponse data. Unsetting Field55");
					isoMsg.unset(55);
				}
				else{
					//[rrabaya 20150331] RM#6046 [MasterCard] No value of IC Data in Auth Response
					//unset the field 55 if the icData is empty string("") or the length of it is less than 1
					internalFormat = super.icResponseDataProcess(internalFormat);
					icData = internalFormat.getValue(IC_INFORMATION.IC_RESPONSE_DATA).getValue().trim();
                    // Start RM6046 No value of IC Data in Auth Response[rrabaya 20150625]
                    // check icData again after isResponseDataProcess
                    if (null != icData  && !NO_SPACE.equals(icData.trim())) {
                           byte[] icDataByte = internalFormat.getValue(
                                         IC_INFORMATION.IC_RESPONSE_DATA).getValue().getBytes();
                           mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Setting Field55");
                           isoMsg.set(55, icDataByte);
                           mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: IC Data: " + ISOUtil.hexdump(icDataByte));
                    } else {
                           mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Removed F55");
                           isoMsg.unset(55);
                    } // End RM6046 [rrabaya 20150331]]
				}
				
			}// Checking of ic response data
			
			// Checking for field 39
			if(isoMsg.hasField(39)){
				// DE 6 - Amount, Cardholder Billing
				if (FLD39_RESPONSE_CODE_PARTIAL_APPROVAL.equals(isoMsg.getString(39)) 
						|| FLD39_RESPONSE_CODE_PURCHASE_AMOUNT_ONLY_NO_CASH.equals(
								isoMsg.getString(39))) {
					isoMsg.unset(6);
				}	
			}
			// End of f39 checking
			
			// [mquines:2011/01/16] For Balance Inquiry
			// DE 54 Amounts
	    	String transactionType = isoMsg.getString(3).substring(0, 2);
	    	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Transaction Type: " + transactionType);
			// Check if Balance Inquiry
	    	if(FLD3_1_TRANSACTION_TYPE_30.equals(transactionType)){
	    		String avaAmount = internalFormat.getValue(
	    				ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.TOTAL_AVA_AMOUNT).getValue();
	    		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Total Available Amount: " + avaAmount);
	    		
	    		if(null != avaAmount && avaAmount.trim().length() > 0){
	    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Total Available Amount: " 
	    					+ ISOUtil.padleft(avaAmount.trim(), 12, '0'));
	    			BigDecimal amount = new BigDecimal(avaAmount);
	    			String sign;
	    			
	    			if(amount.intValue() > 0){
	    				sign = FLD54_4_POSITIVE_C;
	    				
	    			}else{
	    				sign = FLD54_4_NEGATIVE_D;
	    				
	    			} // end of inner if
	    			
	    			isoMsg.set(54, FLD54_1_ACCT_TYPE_30 + FLD54_2_AMT_TYPE_01 + isoMsg.getString(51) + sign +
	    					ISOUtil.padleft(avaAmount.trim(), 12, '0'));
	    			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
	    					"[BanknetIsResponse]: DE 54: " + FLD54_1_ACCT_TYPE_30 + FLD54_2_AMT_TYPE_01 +
	    					isoMsg.getString(49) + sign + ISOUtil.padleft(avaAmount.trim(), 12, '0'));
	    		}// End
			}// End
			 
			// DE 95 Replacement Amounts
			isoMsg.unset(95);

			// DE 127 Private Data
			isoMsg.unset(127);
			isoMsg.unset(61);

		} else if (MTI_0120.equals(messageType)) {
			
			isoMsg.unset(mti0120Fields);

		} else if (MTI_0400.equals(messageType)) {
			isoMsg.unset(mti0400Fields);

			// Field 48 Unset Fields: (0400)
			if (isoMsg.hasField(48)) {
				field48Component = (ISOMsg) isoMsg.getValue(48);
				for (int index : field48Mask) {
					field48Component.unset(index);
				}
			}

		} else if (MTI_0420.equals(messageType)) {

			isoMsg.unset(mti0420Fields);

		}// End of if-else

		isoMsg.setResponseMTI();
		isoMsg.recalcBitMap();
		message.setMessageContent(isoMsg);
		return message;
	}// End of editMsg()

	/**
	 * Helper function for handling editMsg() error 
	 * Since this function is for handling errors, other exceptions encountered 
	 * will be handled here and not thrown to execute()
	 * @param e The Exception
	 */
	private void handleEditMsgError(Exception e) {
		ISOMsg isoMsg;
		
		/*
		 * Edit the ISOMsg according to the InternalFormat If error occurs,
		 *  Output to System Log  *IMCPProcess.writeSysLog(Level.ERROR, "CSW2131")  
		 *  Update Banknet Issuing Log Table 
		 *  Output the Transaction History Log 
		 *  HOST SAF Table Transmission (Reversal)
		 *  Edit the Response Message (Decline) 
		 *  Encode (Banknet) 
		 *  Transmit the Response Message 
		 *  End the process
		 */
		mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131);

		InternalFormat inFormatTemp = parameterMessage.getMessageContent();
		// [MAguila: 20120626] Redmine #3881 -  Added setting of Fep and External Response Code 
		// before sending to Host and Mon Store
		inFormatTemp.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, FEP_RESPONSE_CODE_INVALID_TRANSACTION_IS);
		inFormatTemp.setValue(HOST_COMMON_DATA.RESPONSE_CODE, FLD39_RESPONSE_CODE_INVALID);
		
		isoMsg = (ISOMsg) sharedMemoryMessage.getMessageContent();
		
		parameterMessage.setMessageContent(inFormatTemp);
		/*
		 * Send to transaction history log and HOST SAF
		 */
		parameterMessage.setDataFormatIdentifier(QH_DFI_FEP_MESSAGE);
		parameterMessage.setProcessingTypeIdentifier(QH_PTI_SAF_REQ);
		
		sendMessage(mcpProcess.getQueueName(LN_MON_STORE), parameterMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending decline message to <" 
				+ mcpProcess.getQueueName(LN_MON_STORE) + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + inFormatTemp.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + inFormatTemp.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + inFormatTemp.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + inFormatTemp.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ inFormatTemp.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		sendMessage(mcpProcess.getQueueName(LN_HOST_STORE), parameterMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending decline message to <" 
				+ mcpProcess.getQueueName(LN_HOST_STORE) + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + inFormatTemp.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + inFormatTemp.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + inFormatTemp.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + inFormatTemp.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ inFormatTemp.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		// Edit response (Decline), encode, then send to LCP
		editDecline(isoMsg);
		try {
			insertToLogTable(isoMsg, true, COL_PROCESS_STATUS_APPROVED, COL_PROCESS_RESULT_DECLINE);
		} catch (Exception exception) {
			try {
				transactionManager.rollback();
			} catch (SQLException sqle) {
				args[0]  = sqle.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(sqle));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
				return;
			}
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(exception));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
			return;
		}
		
        byte[] messageArray = pack(isoMsg);
        
        // If error occurs,
        if(messageArray.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse]Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] handleEditMsgError(): End Process");
        	return;
        } // End of if bytesMessage is 0
        
		sharedMemoryMessage.setProcessingTypeIdentifier(QH_PTI_MCP_RES);
		sharedMemoryMessage.setDataFormatIdentifier(QH_DFI_ORG_MESSAGE);
		sharedMemoryMessage.setMessageContent(messageArray);
		
		sendMessage(mcpProcess.getQueueName(LN_BNK_LCP), sharedMemoryMessage);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending decline message to <" 
				+ mcpProcess.getQueueName(LN_BNK_LCP) + ">" +
				"[Process type:" + parameterMessage.getProcessingTypeIdentifier() + 
				"| Data format:" + parameterMessage.getDataFormatIdentifier() +
				"| MTI:" + inFormatTemp.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + parameterMessage.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + inFormatTemp.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + inFormatTemp.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + inFormatTemp.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ isoMsg.getString(39) +"]");
	}// End of handleEditMsgError()

	/**
	 * Helper function for inserting to issuing log table
	 * @param isoMsg The ISOMsg to be inserted
	 * @param forResponse True if for Response
	 * @param status The processing status
	 * @param result The processing result
	 * @throws Exception
	 */
	private void insertToLogTable(ISOMsg isoMsg, boolean forResponse, String status, String result)
			throws Exception {
		InternalFormat internalMessageContent;
		String issuingLogKey;
		String mti;
		String field7;
		String field11;
		String field2;
		String field32;
		String field37;
		String busDate;
		
		/*
		 * Use the internal message to get the issuing log key The issuing log
		 *  key is used for updating the issuing log table
		 *  
		 *  [mqueja:08/10/2011] [Redmine #3254]
		 *  [Updated values of Issuing Log Key to MTI + DE2 + DE7 + DE11 + DE32 + DE37]
		 */
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: insertToLogTable start");
		
		internalMessageContent = (InternalFormat) parameterMessage.getMessageContent();
		isoMsg.setPackager(new GenericValidatingPackager(getPackageConfigurationFile()));
		
		//mti = internalMessageContent.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue();
		mti = isoMsg.getMTI().substring(0, 2)+ PAD_00; 
		field2 = (MTI_0190.equals(mti) ? NO_SPACE : 
			(internalMessageContent.getValue(HOST_COMMON_DATA.PAN).getValue()));
		field11 = internalMessageContent.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue();
		field7 = (internalMessageContent.getValue(HOST_COMMON_DATA.TRANSACTION_DATE).getValue()).substring(4, 8)
				+ internalMessageContent.getValue(HOST_COMMON_DATA.TRANSACTION_TIME).getValue();
		field32 = (MTI_0190.equals(mti) ? NO_SPACE : 
			(internalMessageContent.getValue(HOST_COMMON_DATA.ACQUIRING_INSTITUTION_ID_CODE).getValue()));
		field37 = internalMessageContent.getValue(HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER).getValue();
		
		issuingLogKey = mti + field2 + field7 + field11 + field32 + field37;
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: IssuingLogKey: " + issuingLogKey);
		
		busDate = ISODate.formatDate(new Date(), DATE_FORMAT_yyyyMMdd);
		
        byte[] messageArray = pack(isoMsg);
        
        // If error occurs,
        if(messageArray.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] insertToLogTable(): End Process");
            return;
        } // End of if bytesMessage is 0
       
        
		/*
		 * Set the following fields in domain: Network Key, FEP Sequence,
		 * Business Date, Process Status Process Result, Message Data 
		 * Then insert the updated values into the log table
		 */
		domain.setNw_key(issuingLogKey);
		domain.setFep_seq(new BigDecimal((forResponse) 
				? sharedMemoryMessage.getFEPSeq()
				: mcpProcess.getSequence(SysCode.FEP_SEQUENCE)));
		domain.setPro_status(status);
		domain.setPro_result(result);
		domain.setUpdateId(mcpProcess.getProcessName());
		domain.setBus_date(busDate);
		domain.setMsg_data(messageArray);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Inserting to Issuing Log table:");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Network key: " + domain.getNw_key());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: FEP seq: " + domain.getFep_seq());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Status: " + domain.getPro_status());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Result: " + domain.getPro_result());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Update ID: " + domain.getUpdateId());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Business date: " + domain.getBus_date());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: Msg data: " + domain.getMsg_data());
		
		//[MQuines:12/21/2011] Added try catch handling
		try{
			dao.insert(domain);		
	 		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] Successfull in Domain Insert");
	 		transactionManager.commit();
	 		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] Successfull in Commit");
		}catch (Exception e) {
			try {
	 			transactionManager.rollback();
	 			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] Rollback Occurs");
	 		 
	 		} catch (SQLException se) {  			 
				 args[0]  = se.getMessage();
				 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154);
				 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, EXCEPTION_DB_OPERATION_SAW2154, args);
				 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(se));
				 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] Nothing to Rollback");
	 		 } // End of try catch
	         
			 args[0]  = e.getMessage();
			 sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007);
			 mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7007, args);
			 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(e));
			 mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetIsResponse] execute(): End Process");
			 throw e;
		}// End of try catch
	}// End of insertToLogTable()
	
	/**
     * Helper function to create reversal ISOMsg
	 * @param message The Fep Message received
	 * @throws ISOException
	 * @throws AeonDBException
	 * @throws IllegalParamException
	 * @return ISOMsg The created reversal ISOMsg
     */
	private ISOMsg createReversalAdvice(FepMessage message) throws 
	     IllegalParamException, SharedMemoryException, ISOException, AeonDBException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: processReversalAdvice start");

		int mti0100Fields[] = {18, 61};
		String field90;
		
		ISOMsg reversalIsoMsg = (ISOMsg) message.getMessageContent();
		
		reversalIsoMsg.unset(mti0100Fields); 
		
		// [mqueja:09/06/2011] [added field90 for reference of 0420 to be able to acquire org_stan]
		field90 = reversalIsoMsg.getMTI() + reversalIsoMsg.getString(11) + reversalIsoMsg.getString(7) + 
			reversalIsoMsg.getString(32) + reversalIsoMsg.getString(33);
		
		reversalIsoMsg.set(90, field90);
		
		// create new STAN and transaction date and time for Reversal Message.	
		reversalIsoMsg.setMTI(SysCode.MTI_0420);
		
		reversalIsoMsg.set(11, ISOUtil.padleft(
				(String)mcpProcess.getSequence(SysCode.STAN_SYS_TRACE_AUDIT_NUMBER).trim(), 6, '0'));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: createReversalMsg F11: " 
				+ reversalIsoMsg.getString(11));
		
		reversalIsoMsg.set(7, ISODate.formatDate(new Date(), Constants.DATE_FORMAT_MMDDhhmmss));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: createReversalMsg F7: " 
				+ reversalIsoMsg.getString(7));
		
		reversalIsoMsg.set(12, ISODate.formatDate(new Date(), Constants.DATE_FORMAT_hhmmss_24H));
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse]: createReversalMsg F12: " 
				+ reversalIsoMsg.getString(12));
			
		reversalIsoMsg.recalcBitMap();
			
		return reversalIsoMsg;
	}// End of createReversalAdvice()
	
	/**
     * Helper function to edit decline ISOMsg
	 * @param isoMsg The ISOMsg to be editted
	 * @return ISOMsg The editted ISOMsg
     */
	private ISOMsg editDecline(ISOMsg isoMsg) {
		try {
			isoMsg.setResponseMTI();
			isoMsg.set(39, FLD39_RESPONSE_CODE_INVALID_TRANSACTION);
			isoMsg.recalcBitMap();
			isoMsg.setPackager(packager);
		} catch (ISOException ie) {			
			args[0]  = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] execute(): End Process");
			return null;
		}
		return isoMsg;
	}// End of editDecline
	
	/**
	 * Start RM5850 Reminder for MasterCard April 2014 Compliance - Global 555 Payment Account Status Inquiry Transactions (2013 Enhancement)
	 * Check if an account status inquiry transaction
	 * @return 
	 * 		true : DE61.7 is 8
	 * 		false :DE61.7 not 8
	 * @author rsiaotong : 20140429
	 * @throws ISOException
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
	 * Start RM5850 Reminder for MasterCard April 2014 Compliance - Global 555 Payment Account Status Inquiry Transactions (2013 Enhancement)
	 * Checks invalid response codes for Account Status Inquiry transaction
	 * @return 
	 * 		true : external response code is invalid for ASI transaction
	 * 		false: external response code is valid for ASI transaction
	 * @author rsiatong : 20140429 
	 */
	private boolean isInvalidExtRespCodeForASI(String extRespCode)
			throws ISOException {
		if (Constants.INVALID_EXTRESPCODE_FOR_ASI_LIST.contains(extRespCode)) {
			return true;
		}
		return false;
	}

	
	/**
	 * Related to RM#5850 Mastercard Moneysend Enhancement
	 * unset fields that are not needed for brand response
	 * @param isoMsg
	 * @return editedISOMsg
	 */
	private ISOMsg unsetFields(ISOMsg isoMsg){
		try {
			if(SysCode.MTI_0100.equals(isoMsg.getMTI()) || SysCode.MTI_0110.equals(isoMsg.getMTI())){
				isoMsg = FepUtilities.unsetFor0110(isoMsg);
			} else if (SysCode.MTI_0400.equals(isoMsg.getMTI()) || SysCode.MTI_0410.equals(isoMsg.getMTI())) {
				// [eotayde 12232014: RM 5838] Unset fields for Reversal Response (0410)
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] unsetFields() : Unset for 0410");
				isoMsg = FepUtilities.unsetFor0410(isoMsg);
			}
		} catch (ISOException e){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetIsResponse] unsetFields() : Invalid ISO Message");
		}
		
		return isoMsg;
	}
}// End of class