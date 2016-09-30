/*
 * Copyright (c) 2008-2010 AEON Credit Technology Systems, Inc. All Rights Reserved.VALIDATE_NO_ERROR
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
 * DATE          AUTHOR          REMARKS / COMMENT
 * ====----------======----------=================------------------------------
 * Aug-04-2010   fsamson         Reorganize code.
 * Jun-21-2010   fsamson         Updated
 * May-31-2010   fsamson         V0.0.1   : Updated the ff methods 
 *                                            - edit_0810_message
 *                                            - edit_0630_message
 *                                            - edit_0302_message
 * May-28-2010   fsamson         V0.0.1   : Major updates
 * Apr-23-2010   fsamson         Initial code.
 * 
 */
package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;
import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;
import static com.aeoncredit.fep.core.adapter.brand.common.Keys.XMLTags.PACKAGE_CONFIGURATION_FILE;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Level;
import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericValidatingPackager;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.CommonBrandCommand;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.adapter.brand.common.Keys;
import com.aeoncredit.fep.core.adapter.brand.common.Keys.XMLTags;
import com.aeoncredit.fep.core.adapter.inhouse.hsmthales.utility.commandmessage.HSMCommandA6;
import com.aeoncredit.fep.core.adapter.inhouse.hsmthales.utility.commandmessage.HSMCommandKE;
import com.aeoncredit.fep.core.internalmessage.FepMessage;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.IssuerFileUpdateFieldKey;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT022BnkisDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPT101FileupdateaqtblDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT101FileupdateaqtblDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPT102CommandaqtblDAO;
import com.aeoncredit.fep.framework.dbaccess.FEPT102CommandaqtblDomain;
import com.aeoncredit.fep.framework.dbaccess.FEPTransactionMgr;
import com.aeoncredit.fep.framework.exception.AeonDBException;
import com.aeoncredit.fep.framework.exception.AeonException;
import com.aeoncredit.fep.framework.exception.HSMException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.exception.SharedMemoryException;
import com.aeoncredit.fep.framework.hsmaccess.HSMThalesAccess;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;

/**
 * Execute the control message of the Issuing and the control 
 *      message from the monitor.
 * @author fsamson
 * @version 0.0.1
 * @see com.aeoncredit.fep.core.adapter.brand.common.CommonBrandCommand
 * @since 0.0.1
 */
public class BanknetCommand extends CommonBrandCommand {

	private final List<String> PROCESSING_TYPE_ID_SIGNONOFF_AC_REQUEST_LIST = 
		Arrays.asList(new String[] { SysCode.QH_PTI_PREFIX_SIGN_ON, SysCode.QH_PTI_PREFIX_SIGN_OFF,
				SysCode.QH_PTI_SIGN_ON, SysCode.QH_PTI_SIGN_OFF });

	private SimpleDateFormat sdf_MMDDhhmmss = new SimpleDateFormat(Constants.DATE_FORMAT_MMDDhhmmss);
	private BanknetInternalFormatProcess internalFormatProcess;
	private FEPTransactionMgr transactionMgr;
	private FEPT022BnkisDAO dao;
	private FEPT102CommandaqtblDAO commandAcDao;
	private FEPT101FileupdateaqtblDAO fileupdateAcDao;
	private HSMThalesAccess hsmThalesAccess;
	private ISOMsg isoMsgInstanceVar;

	private int timeoutValue;
	private String isKeyName;
	private String[] args = new String[2];
	private String sysMsgID;


	/**
	 * Constructor Method
	 * @param mcpProcess The instance of AeonProcessImpl
	 * */
	public BanknetCommand(IMCPProcess mcpProcess) {
		super(mcpProcess);
		
		String controlSystemTimer;

		try {
			controlSystemTimer = mcpProcess.getAppParameter(Keys.XMLTags.CONTROL_SYSTEM_TIMER).toString();
			
			if (controlSystemTimer.matches("\\d+")) {
				timeoutValue = Integer.parseInt(controlSystemTimer);
			}

			isKeyName = mcpProcess.getAppParameter(Keys.XMLTags.IS_KEY_NAME).toString();

			packager = new GenericValidatingPackager((String) (mcpProcess
					.getAppParameter(Keys.XMLTags.PACKAGE_CONFIGURATION_FILE)));

			internalFormatProcess = new BanknetInternalFormatProcess(mcpProcess);
			transactionMgr = mcpProcess.getDBTransactionMgr();
			dao = new FEPT022BnkisDAO(transactionMgr);
			commandAcDao = new FEPT102CommandaqtblDAO(transactionMgr);
			fileupdateAcDao = new FEPT101FileupdateaqtblDAO(transactionMgr);
			hsmThalesAccess = HSMThalesAccess.getInstance();
		
		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0] = th.getMessage();
			args[1] = th.getLocalizedMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					"initialization failed");
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					"judge() failed", args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					FepUtilities.getCustomStackTrace(th));
			return;
		}

	}// End of constructor()

	/**
	 * Execute the Control or command message.
	 * 
	 * @param fepMsg
	 *            The received Fep Message
	 */
	public void execute(FepMessage fepMsg) {
		isoMsgInstanceVar = new ISOMsg();

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
				"********** BRAND MCP COMMAND START PROCESS **********");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetCommand] judge() start");
		try {
			// Call judge() method.
			this.judge(fepMsg);

		} catch (ISOException ie) {
			args[0] = ie.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_SOCKETCOM_2003);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_SOCKETCOM_2003, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,
					FepUtilities.getCustomStackTrace(ie));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetCommand] execute() End Process");
			return;

		} catch (SQLException e) {
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,
					FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetCommand] execute() End Process");
			return;
		
		} catch (HSMException e) {
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					Constants.CLIENT_SYSLOG_HSM_SEND_FAIL);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					Constants.CLIENT_SYSLOG_HSM_SEND_FAIL, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,
					FepUtilities.getCustomStackTrace(e));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetCommand] execute() End Process");
			return;

		} catch (AeonDBException ade) {
			args[0] = ade.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR,
					CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR,
					FepUtilities.getCustomStackTrace(ade));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetCommand] execute() End Process");
			return;

		} catch (IllegalParamException ipe) {
			args[0] = ipe.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_DB_7002, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ipe));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		
		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0] = th.getMessage();
			args[1] = th.getLocalizedMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, "judge() failed");
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, "judge() failed",  args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(th));
			return;
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,
   			 "********** BRAND MCP COMMAND END PROCESS **********");
	} // End of execute() method

	/**
	 * Judge the type of message according to the MTI and message format, call the execute() method.
	 * @param fepMsg The received Fep Message to judge
	 * @throws ISOException 
	 * @throws SQLException 
	 * @throws HSMException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @throws SharedMemoryException 
	 * @throws AeonException 
	 */
	private void judge(FepMessage fepMsg) throws ISOException, SQLException, 
	      HSMException, AeonDBException, IllegalParamException, SharedMemoryException, AeonException {
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge() start");

		/*
		 * Retrieve data format identifier
		 * Retrieve processing type identifier
		 */
		String dataFormatId = fepMsg.getDataFormatIdentifier();
		String processingTypeId = fepMsg.getProcessingTypeIdentifier();
		
		// Initialize mti
		String mti = null;
		String transactionCode;

		// Initialize ISO message and internal format
		ISOMsg isoMsg = new ISOMsg();
		InternalFormat internalMsg = null;

		// Retrieve fepMsg content as ISOMsg
		if (SysCode.QH_DFI_ORG_MESSAGE.equals(dataFormatId)) {
			isoMsg = fepMsg.getMessageContent();
			fepMsg.setFEPSeq(ISOUtil.padleft(mcpProcess.getSequence(SysCode.FEP_SEQUENCE),
					7, Constants.cPAD_0));
			mti = isoMsg.getMTI();
		
		} else if (SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatId)) {
			// Retrieve fepMsg content as InternalFormat
			internalMsg = fepMsg.getMessageContent();
			
			// [Lutherford Sosuan : 11/30/2010] [Redmine #1037] [Corrected SS document together with the code]
			if(internalMsg == null){
				mti = null;
				internalMsg = new InternalFormat();
				fepMsg.setMessageContent(internalMsg);
			}
			else {
				mti = internalMsg.getValue(
						InternalFieldKey.OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue();
			}
		}

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] judge : mti : " + mti);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] judge : dataFormatId : " + dataFormatId);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] judge : processingTypeId : " + processingTypeId);

		/*
		 * If (MTI = null or "") && Data format identifier = "01"
		 *  Get transaction code
		 *  If Processing type identifier = "31" or "32" or "33" or "34"
		 *   Call signon_off_ac_request() method
		 *  else If Processing type identifier = "37"
		 *   Call advice_ac_request() method
		 *  else If Processing type identifier = "36"
		 *   Call echo_test_ac_request() method
		 *  else If Transaction Code = "05XXXX"
		 *   Call fileUpdate_ac_request() method
		 */
		if ((null == mti || (NO_SPACE).equals(mti)) && SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatId)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : mti is null and DFI is 01");

			transactionCode = NO_SPACE;
			
			if (internalMsg.getValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.
					TRANSACTION_CODE).isNull()) {
				transactionCode = NO_SPACE;
			
			} else{
				transactionCode = internalMsg.getValue
				(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.TRANSACTION_CODE).getValue();
			}
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : transactionCode : " + transactionCode);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : PTI : " + processingTypeId);

			if (PROCESSING_TYPE_ID_SIGNONOFF_AC_REQUEST_LIST.contains(processingTypeId)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] judge : PTI is either 31 or 32 or 33 or 34");

				this.signon_off_ac_request(fepMsg);

			} else if (SysCode.QH_PTI_ADVICE_MESSAGE.equals(processingTypeId)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : PTI is 37");

				this.advice_ac_request(fepMsg);

			} else if (SysCode.QH_PTI_ECHO_TEST.equals(processingTypeId)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : PTI is 36");

				this.echo_test_ac_request(fepMsg);

			} else if (null != transactionCode && transactionCode.matches(
					Constants.TRANS_CODE_REGEX_05XXXX)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] judge : Transaction Code = 05XXXX");

				this.fileUpdate_ac_request(fepMsg);
			}
		}// End of If (MTI = null or "") && Data format identifier = "01"
		
		/*
		 * else If MTI = "0800" (ISO)
		 *  If Field70 = "161"
		 *   Call key_change_is() method
		 *  If Field70 = "270"
		 *   Call echo_test_is() method.
		 */
		else if (SysCode.MTI_0800.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : mti is 0800 (ISO)");

			if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_161.equals(isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand]: judge() : Field70 is 161");

				this.key_change_is(fepMsg);

			} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_270.equals(
					isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand]: judge() : Field70 is 270");
				
				this.echo_test_is(fepMsg);
			}
		} // End of else if MTI = "0800" (ISO)
		
		/*
		 * else If MTI = "0620" && Data format identifier = "10"
		 *  Call management_notification_is_request() method
		 */
		else if (SysCode.MTI_0620.equals(mti) && SysCode.QH_DFI_ORG_MESSAGE.equals(dataFormatId)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : mti is 0620 && DFI is 10");

			this.management_notification_is_request(fepMsg);
		} // End of else If MTI = "0620" && Data format identifier = "10"
		
		/*
		 * else If MTI = "0620" && Data format identifier = "01"
		 *  Call management_notification_is_response() method
		 */
		else if (SysCode.MTI_0620.equals(mti) && SysCode.QH_DFI_FEP_MESSAGE.equals(dataFormatId)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : mti is 0620 && DFI is 01");
			
			this.management_notification_is_response(fepMsg);
		} //End of else If MTI = "0620" && Data format identifier = "01"
		
		/*
		 * else If MTI = "0810"
		 *  If Field70 = "001" or "002" or "061" or "062"
		 *   Call signon_off_ac_response() method
		 *  If Field70 = "060"
		 *   Call advice_ac_response() method
		 *  If Field70 = "270"
		 *   Call echo_test_ac_response() method
		 */ 
		else if (SysCode.MTI_0810.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : mti is 0810");
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] judge : field 70 : " + isoMsg.getString(70));
			
			if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_001.equals(isoMsg.getString(70))
					|| Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_002.equals(
							isoMsg.getString(70))
					|| Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(
							isoMsg.getString(70))
					|| Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(
							isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] judge : Field70 is either 001 or 002 or 061 or 062");

				this.signon_off_ac_response(fepMsg);

			} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_060.equals(
					isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : Field70 is 060");

				this.advice_ac_response(fepMsg);
			
			} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_270.equals(
					isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : Field70 is 270");

				this.echo_test_ac_response(fepMsg);
			}
		} // End of else If MTI = "0810"

		/*
		 * else If MTI = "0820"
		 *  If Field70 = "161"
		 *   Call key_reflection_notification_is() method
		 */
		else if (SysCode.MTI_0820.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : mti is 0820");

			if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_161.equals(isoMsg.getString(70))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : field 70 is 161");

				this.key_reflection_notification_is(fepMsg);
			}
		} // End of else If MTI = "0820"
		
		/*
		 * else If MTI = "0312"
		 *  Call fileUpdate_ac_response() method
		 */
		else if (SysCode.MTI_0312.equals(mti)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] judge : mti is 0312");

			this.fileUpdate_ac_response(fepMsg);
		} // End of else If MTI = "0312"
	}// End of judge()

	/**
	 * Execute signon-off acquiring request message.
	 * @param fepMsg The Fep Message to execute where the message content is InternalFormat (DFI: 01)
	 * @throws ISOException 
	 * @throws SQLException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @since 0.0.1
	 */
	private void signon_off_ac_request(FepMessage fepMsg) throws ISOException,
			SQLException, AeonDBException, IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] signon_off_ac_request() start" );

		ISOMsg isoMsg = null;
		byte[] encodedMsg;
		String msgProcessingNo;
		FEPT102CommandaqtblDomain domain;
		String networkKey;
		String busDate;
		String origPTI = fepMsg.getProcessingTypeIdentifier();
		
		// Call edit_0800_message() method
		try {
			isoMsg = this.edit_0800_message(fepMsg);

		} catch (Throwable th) {
			this.mcpProcess.error(th.getMessage(), th);
			args[0] = th.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute(): End Process");
			return;
		}

		/*
		 * Encode the message.
		 * Write the Shared Memory(Internal format) Get the message FEP sequence No.
		 */
		encodedMsg = pack(isoMsg);
        
        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BankentCommand] Error in Packing Message");
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
            		"[BankentCommand] signon_off_ac_request() End Process");
            return;
        } // End of if bytesMessage is 0 

        /* 
		 * [MQuines: 10/03/2011] Create new InternalFormat [MQuines: 11/21/2011]
		 * Modified implementation from InternalFormat inFormat = new
		 * InternalFormat() to InternalFormat inFormat =
		 * internalFormatProcess.createInternalFormatFromISOMsg(isoMsg)
         */
		InternalFormat inFormat = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
        
		try {
			inFormat.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), PROCESS_I_O_TYPE_ACREQ);
		
        } catch (SharedMemoryException sme) {
        	args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
            		"[BanknetCommand] signon_off_ac_request() SharedMemoryException Occured");
        }

		// [MQuines: 10/03/2011] Set updated internal format in fepmsg
        fepMsg.setMessageContent(inFormat);
		fepMsg.setFEPSeq(ISOUtil.padleft(mcpProcess.getSequence(SysCode.FEP_SEQUENCE), 7, Constants.cPAD_0));
		msgProcessingNo = fepMsg.getFEPSeq();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] signon_off_ac_request : msgProcessingNo : " + msgProcessingNo);
		
		// Call super.setTransactionMessage() method
		setTransactionMessage(msgProcessingNo, timeoutValue, fepMsg);

		/*
		 * Transmit the Request Message
		 * Edit the queue header
		 * Processing type identifier = "03" Data format identifier = "10"
		 * Set the encoded message
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
		fepMsg.setMessageContent(encodedMsg);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send  the message to the queue of LCP
		 * queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 */
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving message from <" + 
		mcpProcess.getQueueName(SysCode.LN_MON_MCP) + ">");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <"
				+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process Type: " + fepMsg.getProcessingTypeIdentifier() + 
				"| Data Format: " + fepMsg.getDataFormatIdentifier() +
				"| MTI: " + isoMsg.getMTI() + 
				"| fepSeq: " + fepMsg.getFEPSeq() + 
				"| Network ID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response COde: " + (
						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
		/*
		 * *note for step5: If we want to sign-on and send sign-on request, if
		 * NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG = "1", then whether response
		 * successfully or not, we will set the network status as sign-on.
		 * 
		 * If sign-on request
		 * If NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG = "1"
		 *  Call super.signOnOff(1) method
		 */
		String noSignOnRespAqFlag = (String) mcpProcess.getAppParameter(
				XMLTags.NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG);
		
		// [ACSS)EOtayde 06162014: RM 5529] Added prefix sign-on
		if ((SysCode.QH_PTI_SIGN_ON.equals(origPTI)) || (SysCode.QH_PTI_PREFIX_SIGN_ON.equals(origPTI))) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] signon_off_ac_request : sign-on request");

			// If NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG = "1"
			if (NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG.equals(noSignOnRespAqFlag)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] signon_off_ac_request : NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG = " + 
						noSignOnRespAqFlag);
				
				// 5.1 Call super.signOnOff(1) method.
				signOnOff(SysCode.SIGN_ON);
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] signon_off_ac_request : SignOnOff Value = " + SysCode.SIGN_ON);
			} // End of if NO_SIGN_ON_RESPONSE_ACQUIRING_SEND_FLAG = "1"
		} // End of if sign-on request
		
		/*
		 * Insert Command Acquiring Log Table
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		domain = new FEPT102CommandaqtblDomain();
		networkKey = SysCode.BKN + isoMsg.getMTI() + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] signon_off_ac_request : Network Key : " + networkKey);
		
		domain.setNw_key(networkKey);
		domain.setFep_seq(new BigDecimal(msgProcessingNo));
		domain.setUpdateId(mcpProcess.getProcessName());
		busDate = fepMsg.getDateTime().substring(0, 8);
		domain.setBus_date(busDate);
		
		try {
			commandAcDao.insert(domain);
			transactionMgr.commit();
		
		} catch (Exception ex) {
			args[0] = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute(): End Process");
			transactionMgr.rollback();
			return;
		}

	}// End of signon_off_ac_request()

	/**
	 * Execute signon-off acquiring response message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws SQLException 
	 * @throws ISOException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void signon_off_ac_response(FepMessage fepMsg) throws SQLException, ISOException, SharedMemoryException {
		mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] signon_off_ac_response() start");

		//  Check the message format according to the MTI create ISOMsg from FepMessage
		ISOMsg isoMsg = fepMsg.getMessageContent();
		InternalFormat internalFormatErr;
		String msgSeqNum;
		FepMessage fepMsgFromSharedMemory;
		InternalFormat internalMsg;
		String networkKey;
		FEPT102CommandaqtblDomain domain;
		List<FEPT102CommandaqtblDomain> result = null;
		String fepCode;
		String pti;
		String tempPan;

		/*
		 * validate ISOMsg If error occurs, output the System Log create
		 * internal format added timestamp
		 */
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);
			
			internalFormatErr = internalFormatProcess .createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ACRES);

			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 * (set "signon_off error" into PAN field for the display in transaction history)
			 * 
			 * [mqueja:05/19/2011] [#2579] [Added Network Name for SignOn/Off Transaction]
			 * [mquines:11/21/2011] Modified the logic setting of Message Error in PAN
			 * if F70 = 061 || 001, 
			 * Set internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_ERROR_SIGNONON_BNK)
			 * if F70 = 062 || 002, 
			 * Set internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_ERROR_SIGNONOFF_BNK)
			 */
			if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(isoMsg.getString(70)) ||
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_001.equals(isoMsg.getString(70))){
				internalFormatErr.setValue(
						InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000001);
				internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_ERROR_SIGNONON_BNK);
			
			} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(isoMsg.getString(70)) ||
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_002.equals(isoMsg.getString(70))){
				internalFormatErr.setValue(
						InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000002);
				internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_ERROR_SIGNONOFF_BNK);
			}// End of if-else

			// [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send 
			 * the message to the queue of Monitor STORE process
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			
			// End the process
			return;
		}

		/*
		 * get Fep Sequence by searching Command Acquiring Log Table for the following use
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		networkKey = SysCode.BKN + SysCode.MTI_0800 + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] signon_off_ac_response : Network Key : " + networkKey);
		
		domain = new FEPT102CommandaqtblDomain();
		domain.setNw_key(networkKey);
		
		try {
			result = commandAcDao.findByCondition(domain);
		
		} catch (Exception ex) {
			args[0] = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute(): End Process");
			return;
		}

		if (null == result || result.isEmpty()) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand]: signon_off_ac_response() : no results found");
			return;
		}

		/*
		 * Get the message FEP sequence No
		 * Call super.getTransactionMessage() method
		 * added timestamp to internal format
		 */
		msgSeqNum = result.get(0).getFep_seq() + NO_SPACE;
		msgSeqNum = ISOUtil.padleft(msgSeqNum, 7, Constants.cPAD_0);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] signon_off_ac_response : message Sequence Number : " + msgSeqNum);

		fepMsgFromSharedMemory = getTransactionMessage(msgSeqNum);
         // [MAguila: 20120619] Redmine #3860 Updated Logging
		if(null == fepMsgFromSharedMemory) {
			args[0] = msgSeqNum;
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205, args);               
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] : sharedFepMessage is null. Return from execute()");
			return;
		}
        
		internalMsg = fepMsgFromSharedMemory.getMessageContent();
		internalMsg.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
				Constants.PROCESS_I_O_TYPE_ACRES);
		pti = fepMsgFromSharedMemory.getProcessingTypeIdentifier();
		
		try {
			// mapControl_info_part - Capture card flag (Edit according to response code)
			internalMsg.setValue(InternalFieldKey.CONTROL_INFORMATION.CAPTURE_CARD_FLAG, 
					(Constants.FLD39_RESPONSE_CODE_CAPTURED_CARD.equals(isoMsg.getString(39)) 
							? "1" 
							: "0"));

			// mapHost_header - REQUEST/RESPONSE INDICATOR (Set '2')
			internalMsg.setValue(InternalFieldKey.HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR,
					Constants.HOST_RESPONSE);

			// mapHost_header - AUTHORIZATION JUDGMENT DIVISION ('4'(Acquiring))
			internalMsg.setValue(InternalFieldKey.HOST_HEADER
					.AUTHORIZATION_JUDGMENT_DIVISION, Constants.AUTHORIZATION_JUDGMENT_DIVISION_4);

            /*
             *  [emaranan 12-14-2011] If F39 is present, 
             *      set CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
             *      else no need to set any value.
             */
            if (isoMsg.hasField(39)) {
                fepCode = changeToFEPCode(Constants.OUTRESCNV_NW_CODE_BANKNET, isoMsg.getString(39));
                internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, isoMsg.getString(39));
                internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepCode); 
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
                        "[BanknetCommand] signon_off_ac_response : fep Response Code : " + fepCode);
            } // End of IF statement
            
		} catch (Throwable th) {
			this.mcpProcess.error(th.getMessage(), th);			
			args[0]  = th.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] signon_off_ac_response : Field70 : " + isoMsg.getString(70));

		/*
		 * If MTI = "0810" and (DE70 = "061" or DE70 = "062")
		 *  If DE70 = "061"
		 *   Call super.signOnOff() method SIGN_ON
		 *  Else If DE70 = "062"
		 *   Call super.signOnOff() method SIGN_OFF
		 *   (If DE70 = "062"(sign-off),set "Signon_off(BANKNET)" into PAN field for the display in transaction history)
		 */
		if (SysCode.MTI_0810.equals(isoMsg.getMTI())) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] signon_off_ac_response : mti is 0810");
			
			// [ACSS)NRomeo 11062014] start RM#5877 : [MasterCard] Handling of response code for sign-on/sign-off		
			if (Constants.FLD39_RESPONSE_CODE_SUCCESS.equals(isoMsg.getString(39))){				
				if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(isoMsg.getString(70))) {
					// [ACSS)EOtayde 09222014] Change literal value displayed in log to the actual retrieved value
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetCommand] signon_off_ac_response : Field70 is " + isoMsg.getString(70));
					
					signOnOff(SysCode.SIGN_ON);
					
					/* [maguila: 05/24/2011] [Redmine #2579] [Output the transaction history log.
					 * (If DE70 = "061"(sign-on), 
					 *   set "Signon_on(BANKNET)" into PAN field for the display in transaction history)]
					 * [mqueja:8/12/2011] [added setting of transaction code]
					 */
					
					//internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_SUCCESS_SIGNONON_BNK);
					
					/*
					 * [ACSS)EOtayde 08042014: RM 5742] BANKNET - Display Prefix Sign-on/off
					 *   Added the condition which determines if the command executed 
					 *   was prefix sign-on or sign-on then set the message.
					 */
					if (pti.equals(SysCode.QH_PTI_PREFIX_SIGN_ON)) {
						// internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
						// Constants.MSG_SUCCESS_PRE_SIGNONON_BNK);
	
						tempPan = isoMsg.getString(2) + Constants.DASH + Constants.MSG_SUCCESS_PRE_SIGNONON_BNK;
						
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								tempPan);
					} else {
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								Constants.MSG_SUCCESS_SIGNONON_BNK);
					}
					
					internalMsg.setValue(
							InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000001);
	
				} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(isoMsg.getString(70))) {
					// [ACSS)EOtayde 09222014] Change literal value displayed in log to the actual retrieved value
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetCommand] signon_off_ac_response : Field70 is " + isoMsg.getString(70));
					
					signOnOff(SysCode.SIGN_OFF);
					//internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_SUCCESS_SIGNONOFF_BNK);
					
					/*
					 * [ACSS)EOtayde 08042014: RM 5742] BANKNET - Display Prefix Sign-on/off
					 *   Added the condition which determines if the command executed 
					 *   was prefix sign-off or sign-off then set the message.
					 */
					if (pti.equals(SysCode.QH_PTI_PREFIX_SIGN_OFF)) {					
						tempPan = isoMsg.getString(2) + Constants.DASH + Constants.MSG_SUCCESS_PRE_SIGNONOFF_BNK;
						
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								tempPan);
					} else {
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								Constants.MSG_SUCCESS_SIGNONOFF_BNK);
					}
					
					internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, 
							Constants.TRANS_CODE_000002);
				}
				
				// DE70 = "061"
				if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(isoMsg.getString(70))) {
					// [ACSS)EOtayde 09122014: RM 5787] Updated logging from 001 to 061
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetCommand] signon_off_ac_response : Field70 is " + isoMsg.getString(70));
					
					// [mqueja:8/12/2011] [added setting of transaction code]
					internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, 
							Constants.TRANS_CODE_000001);
					
					internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_SUCCESS_SIGNONON_BNK);
					
					/*
					 * [ACSS)EOtayde 08042014: RM 5742] BANKNET - Display Prefix Sign-on/off
					 *   Added the condition which determines if the command executed 
					 *   was prefix sign-on or sign-on then set the message.
					 */
					if (pti.equals(SysCode.QH_PTI_PREFIX_SIGN_ON)) {
						// internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
						// Constants.MSG_SUCCESS_PRE_SIGNONON_BNK);
		
						tempPan = isoMsg.getString(2) + Constants.DASH + Constants.MSG_SUCCESS_PRE_SIGNONON_BNK;
						
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								tempPan);
					} else {
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								Constants.MSG_SUCCESS_SIGNONON_BNK);
					}
				
				// DE70 = "062"
				} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(isoMsg.getString(70))) {
					// [ACSS)EOtayde 09122014: RM 5787] Updated logging from 002 to 062
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetCommand] signon_off_ac_response : Field70 is " + isoMsg.getString(70));
		
					// [mqueja:8/12/2011] [added setting of transaction code]
					internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, 
							Constants.TRANS_CODE_000002);
					//internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_SUCCESS_SIGNONOFF_BNK);
					
					/*
					 * [ACSS)EOtayde 08042014: RM 5742] BANKNET - Display Prefix Sign-on/off
					 *   Added the condition which determines if the command executed 
					 *   was prefix sign-off or sign-off then set the message.
					 */
					if (pti.equals(SysCode.QH_PTI_PREFIX_SIGN_OFF)) {					
						tempPan = isoMsg.getString(2) + Constants.DASH + Constants.MSG_SUCCESS_PRE_SIGNONOFF_BNK;
						
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								tempPan);
					} else {
						internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
								Constants.MSG_SUCCESS_SIGNONOFF_BNK);
					}				
				} 
			} else {				
				if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(isoMsg.getString(70))){
					signOnOff(SysCode.SIGN_OFF);
					internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000001);
					internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_ERROR_SIGNONON_BNK);	
				} else if (Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(isoMsg.getString(70))){
					signOnOff(SysCode.SIGN_ON);
					internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000002);
					internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_ERROR_SIGNONOFF_BNK);				
				}
			} // [ACSS)NRomeo 11062014] end RM#5877 : [MasterCard] Handling of response code for sign-on/sign-off	
		} 

		/* update FepMessage
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		internalMsg.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
		fepMsgFromSharedMemory.setMessageContent(internalMsg);
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*  Call sendMessage(String queueName, FepMessage message)
		 *  to send the message to the queue of Monitor STORE 
		 *  process.  *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer to "SS0111 System Code 
		 *  Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsgFromSharedMemory);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsgFromSharedMemory.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsgFromSharedMemory.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsgFromSharedMemory.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of signon_off_ac_response() method

	/**
	 * Execute advice acquiring request message.
	 * @param fepMsg The Fep Message to execute where the message content is InternalFormat (DFI: 01)
	 * @throws ISOException 
	 * @throws SQLException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @since 0.0.1
	 */
	private void advice_ac_request(FepMessage fepMsg) throws ISOException, SQLException, AeonDBException, 
	          IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] advice_ac_request() start");

		ISOMsg isoMsg = null;
		byte[] encodedMsg;
		String msgProcessingNo;
		FEPT102CommandaqtblDomain domain;
		String networkKey;
		String busDate;

		// Call edit_0800_message() method
		try {
			isoMsg = this.edit_0800_message(fepMsg);

		} catch (Throwable th) {
			this.mcpProcess.error(th.getMessage(), th);			
			args[0]  = th.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		/*
		 * Encode the message.
		 * Write the Shared Memory(Internal format)
		 * Get the message FEP sequence No.
		 * Call super.setTransactionMessage() method
		 */
		encodedMsg = pack(isoMsg);

        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BankentCommand] Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
        			"[BankentCommand] advice_ac_request() End Process");
        	return;
        } // End of if bytesMessage is 0 
        
		//[MQuines: 11/21/2011] Added creation of internal format. 
		InternalFormat inFormat = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
        try {
			inFormat.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), PROCESS_I_O_TYPE_ACREQ);
		
        } catch (SharedMemoryException sme) {
        	args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
            		"[BanknetCommand] advice_ac_request() SharedMemoryException Occured");
        }
		
        fepMsg.setMessageContent(inFormat);
		fepMsg.setFEPSeq(ISOUtil.padleft(mcpProcess.getSequence(SysCode.FEP_SEQUENCE), 7, Constants.cPAD_0));
		
		msgProcessingNo = fepMsg.getFEPSeq();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] advice_ac_request : message Processing Number : " + msgProcessingNo);
		
		setTransactionMessage(msgProcessingNo, timeoutValue, fepMsg);

		/*
		 * Transmit the Request Message
		 * Edit the queue header
		 * Processing type identifier = "03"  Data format identifier = "10"
		 * set the encoded message
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
		fepMsg.setMessageContent(encodedMsg);
			
		/*
		 *  Call sendMessage(String queueName, FepMessage message) to send the message to the queue of LCP.
		 *  *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 */
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "Logsequence: " + fepMsg.getLogSequence());
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving message from <" + 
		mcpProcess.getQueueName(SysCode.LN_MON_MCP) + ">");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <" + 
		mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process Type: " + fepMsg.getProcessingTypeIdentifier() + 
				"| Data Format: " + fepMsg.getDataFormatIdentifier() +
				"| MTI: " + isoMsg.getMTI() + 
				"| fepSeq: " + fepMsg.getFEPSeq() + 
				"| Network ID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response COde: " + (
						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
		/*
		 * Insert Command Acquiring Log Table
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		domain = new FEPT102CommandaqtblDomain();
		networkKey = SysCode.BKN + isoMsg.getMTI() + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] advice_ac_request() Network Key : " + networkKey);
		
		domain.setNw_key(networkKey);
		domain.setFep_seq(new BigDecimal(msgProcessingNo));
		domain.setUpdateId(mcpProcess.getProcessName());
		busDate = fepMsg.getDateTime().substring(0, 8);
		domain.setBus_date(busDate);
		
		try {
			commandAcDao.insert(domain);
			transactionMgr.commit();
			
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
			transactionMgr.rollback();
			return;
		}
	} // End of advice_ac_request() method

	/**
	 * Execute advice acquiring response message.
	 * @param fepMssg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws SQLException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void advice_ac_response(FepMessage fepMsg) throws SQLException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] advice_ac_response() start");

		/*
		 * Check the message format according to the MTI
		 * create ISOMsg from FepMessage
		 */
		ISOMsg isoMsg = fepMsg.getMessageContent();
		String sysMsgID;
		InternalFormat internalFormatErr;
		String networkKey;
		FEPT102CommandaqtblDomain domain;
		List<FEPT102CommandaqtblDomain> result;
		String msgSeqNum;
		FepMessage fepMsgFromSharedMemory;
		InternalFormat internalMsg;

		/*
		 * validate ISOMsg
		 * If error occurs output the System Log
		 *  create internal format
		 */
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);

			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ACRES);
			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 *  (set "advice error" into PAN field for the display in transaction history)
			 * [mqueja:8/18/2011] [added setting of source id and transaction code]
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_ERROR_ADVICE_BNK);
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000007);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send the message to the queue of 
			 * Monitor STORE process.
			 *  *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			//  End the process
			return;
		}

		/*
		 * get Fep Sequence by searching Command Acquiring Log Table for the following use
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		networkKey = SysCode.BKN + SysCode.MTI_0800 + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] advice_ac_response : Network Key : " + networkKey);
		
		domain = new FEPT102CommandaqtblDomain ();
		domain.setNw_key(networkKey);
		result = null;
		
		try {
			result = commandAcDao.findByCondition(domain);
			
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
			return;
		}

		if (null == result || result.isEmpty()) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] advice_ac_response : result is null");
			return;
		}

		/*
		 * Get the original message
		 * Get the message FEP sequence No
		 * Call super.getTransactionMessage() method
		 * added timestamp
		 */
		msgSeqNum = result.get(0).getFep_seq() + NO_SPACE;
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] advice_ac_response : message Sequence Number : " + msgSeqNum);

		fepMsgFromSharedMemory = getTransactionMessage(msgSeqNum);
        // [MAguila: 20120619] Redmine #3860 Updated Logging
		if(null == fepMsgFromSharedMemory) {
			args[0] = msgSeqNum;
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205, args);                
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] advice_ac_response : sharedFepMessage is null. Return from execute()");
			return;
		}
        
		internalMsg = fepMsgFromSharedMemory.getMessageContent();
		internalMsg.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
				PROCESS_I_O_TYPE_ACRES);
		try {
			/*
			 * Update Internal Format according to the ISOMsg mapControl_info_part
			 * Network Authorization Result 
			 * If Field 39 is in ("00', '08', '10', '87') set '1'(Appove)
			 * else set Constants.cPAD_0(Decline) )
			 * mapControl_info_part - Capture card flag 
			 * (Edit according to response code)
			 */
			internalMsg.setValue(InternalFieldKey.CONTROL_INFORMATION.CAPTURE_CARD_FLAG, 
					(FLD39_RESPONSE_CODE_CAPTURED_CARD.equals(
							isoMsg.getString(39)) ? "1" : "0"));

			// mapHost_header - REQUEST/RESPONSE INDICATOR (Set '2')
			internalMsg.setValue(InternalFieldKey.HOST_HEADER
					.REQUEST_OR_RESPONSE_INDICATOR,SysCode.HOST_REQ_MANUAL_AUTHORIZATION);

			// mapHost_header - AUTHORIZATION JUDGMENT DIVISION ('4'(Acquiring))
			internalMsg.setValue(InternalFieldKey.HOST_HEADER
					.AUTHORIZATION_JUDGMENT_DIVISION, SysCode.HOST_REQUEST_HSM_RELATE);
            
            /*  [emaranan 12-14-2011] If F39 is present, 
             *   set CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
             *  else no need to set any value.
             */
            if (isoMsg.hasField(39)) {
                String fepCode;
                internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, isoMsg.getString(39));
                fepCode = changeToFEPCode(Constants.OUTRESCNV_NW_CODE_BANKNET, isoMsg.getString(39));
                internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepCode); 
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
                        "[BanknetCommand] advice_ac_response : fep response code : " + fepCode);
            } // End of IF statement

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		/*
		 * Call super.adviceSignOnOff() method.
		 * Output the transaction history log
		 * (set "advice" into PAN field for the display in transaction history)
		 */
		adviceSignOnOff(getAdviceStatus());
		internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_SUCCESS_ADVICE_BNK);
		
		// [mqueja:8/18/2011] [added setting of source id and transaction code]
		internalMsg.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
		internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000007);
		
		/*
		 * update FepMessage
		 * Edit the queue header 
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsgFromSharedMemory.setMessageContent(internalMsg);
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * > Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * > *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsgFromSharedMemory);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsgFromSharedMemory.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsgFromSharedMemory.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsgFromSharedMemory.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of advice_ac_response() method

	/**
	 * Execute echo-test acquiring request message.
	 * @param fepMsg The Fep Message to execute where the message content is InternalFormat (DFI: 01)
	 * @throws ISOException 
	 * @throws SQLException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @since 0.0.1
	 */
	private void echo_test_ac_request(FepMessage fepMsg) throws ISOException, SQLException, AeonDBException, 
		  IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] echo_test_ac_request() start");

		ISOMsg isoMsg = null;
		byte[] encodedMsg;
		String msgProcessingNo;
		FEPT102CommandaqtblDomain domain;
		String networkKey;
		String busDate;

		try {
			// Call edit_0800_message() method
			isoMsg = this.edit_0800_message(fepMsg);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		// Encode the message.
		encodedMsg = pack(isoMsg);

        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BankentCommand] Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
        			"[BankentCommand] echo_test_ac_request() End Process");
        	return;
        } // End of if bytesMessage is 0 
        
        // [MQuines: 10/03/2011] Create new InternalFormat
		InternalFormat inFormat = new InternalFormat();
        try {
			inFormat.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), PROCESS_I_O_TYPE_ACREQ);
			
		} catch (SharedMemoryException sme) {
			args[0]  = sme.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_2020);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_INFORMATION, SERVER_APPLOG_INFO_2020, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, FepUtilities.getCustomStackTrace(sme));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
            		"[BanknetCommand] echo_test_ac_request() : SharedMemoryException Occured");
        }
		
		/*
		 * Write the Shared Memory(Internal format)
		 * Get the message FEP sequence No.
		 * [MQuines: 10/03/2011] Set updated internal format in fepmsg
		 */
        fepMsg.setMessageContent(inFormat);
		fepMsg.setFEPSeq(ISOUtil.padleft(mcpProcess.getSequence(SysCode.FEP_SEQUENCE), 7, Constants.cPAD_0));

		msgProcessingNo = fepMsg.getFEPSeq();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand]: echo_test_ac_request() : message processing number : " + msgProcessingNo);

		/*
		 * Call super.setTransactionMessage() method
		 * Transmit the Request Message
		 * Edit the queue header
		 * Processing type identifier = "03" Data format identifier = "10"
		 */
		setTransactionMessage(msgProcessingNo, timeoutValue, fepMsg);
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

		/*
		 * set the encoded message
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of LCP.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),refer to
		 * "SS0111 System Code Definition(FEP).xls"
		 */ 
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		fepMsg.setMessageContent(encodedMsg);
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving message from <" + 
		mcpProcess.getQueueName(SysCode.LN_MON_MCP) + ">");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <" + 
				mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process Type: " + fepMsg.getProcessingTypeIdentifier() + 
				"| Data Format: " + fepMsg.getDataFormatIdentifier() +
				"| MTI: " + isoMsg.getMTI() + 
				"| fepSeq: " + fepMsg.getFEPSeq() + 
				"| Network ID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response Code: " + (
						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
		/*
		 * Insert Command Acquiring Log Table
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		domain = new FEPT102CommandaqtblDomain ();
		networkKey = SysCode.BKN + isoMsg.getMTI() + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] echo_test_ac_request : Network Key : " + networkKey);
		
		domain.setNw_key(networkKey);
		domain.setFep_seq(new BigDecimal(msgProcessingNo));
		domain.setUpdateId(mcpProcess.getProcessName());
		busDate = fepMsg.getDateTime().substring(0, 8);
		domain.setBus_date(busDate);
		
		try {
			commandAcDao.insert(domain);
			transactionMgr.commit();
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
			transactionMgr.rollback();
			return;
		}
	} // End of echo_test_ac_request() method

	/**
	 * Execute echo-test acquiring response message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws SQLException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void echo_test_ac_response(FepMessage fepMsg) throws ISOException, SQLException, SharedMemoryException,
		  AeonException{
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] echo_test_ac_response() start");

		/*
		 * Check the message format according to the MTI
		 * create ISOMsg from FepMessage
		 */
		ISOMsg isoMsg = fepMsg.getMessageContent();
		InternalFormat internalFormatErr;
		String networkKey;
		String msgSeqNum;
		FepMessage fepMsgFromSharedMemory;
		InternalFormat internalMsg;
		FEPT102CommandaqtblDomain domain;
		List<FEPT102CommandaqtblDomain> result = null;

		// validate ISOMsg
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			/*
			 * If error occurs output the System Log
			 * create internal format
			 */
			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);

			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ACRES);			
			
			/*
			 * Output the Transaction History Log(Output the Transaction  History Log)
			 * (set "echo_test error" into PAN field for the display in transaction history)
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,Constants.MSG_ERROR_ECHOTEST_BNK);
			internalFormatErr.setValue(HOST_HEADER.SOURCE_ID, OUTRESCNV_NW_CODE_BANKNET);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			// End the process
			return;
		}

		/*
		 * get Fep Sequence by searching Command Acquiring Log Table for the following use
		 * Net Work Key = "BKN" + MTI + Systems Trace Audit Number(STAN)
		 */
		networkKey = SysCode.BKN + SysCode.MTI_0800 + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] echo_test_ac_response : Network Key : " + networkKey);
		domain = new FEPT102CommandaqtblDomain();
		domain.setNw_key(networkKey);
		
		try {
			result = commandAcDao.findByCondition(domain);
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
			return;
		}

		if (result == null || result.isEmpty()) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] echo_test_ac_response : no record found");
			return;
		}

		/* Get the original message
		 * Get the message FEP sequence No
		 */
		msgSeqNum = ISOUtil.padleft(result.get(0).getFep_seq() + NO_SPACE, 7, Constants.cPAD_0);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] echo_test_ac_response : message Sequence Number : " + msgSeqNum);

		// Call super.getTransactionMessage() method
		fepMsgFromSharedMemory = getTransactionMessage(msgSeqNum);
		// [MAguila: 20120619] Redmine #3860 Updated Logging
		if(null == fepMsgFromSharedMemory) {
			args[0] = msgSeqNum;
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205, args);         
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] echo_test_ac_response : sharedFepMessage is null. Return from execute");
			return;
		}
        
		internalMsg = fepMsgFromSharedMemory.getMessageContent();
		internalMsg.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
				Constants.PROCESS_I_O_TYPE_ACRES);
		try {
			/*
			 * Update Internal Format according to the ISOMsg
			 * mapControl_info_part - Network Authorization Result ( If Field 39
			 * is in ("00', '08', '10', '87') set '1'(Appove) else set Constants.cPAD_0
			 * (Decline) )
			 *
			 * mapControl_info_part - Original Network Response Code (Field 39)
			 * mapControl_info_part - Capture card flag (Edit according to response code)
			 */
			internalMsg.setValue(InternalFieldKey.CONTROL_INFORMATION.CAPTURE_CARD_FLAG, 
					(Constants.FLD39_RESPONSE_CODE_CAPTURED_CARD.equals(isoMsg.getString(39)) 
							? "1" 
							: "0"));

			// mapHost_header - REQUEST/RESPONSE INDICATOR (Set '2')
			internalMsg.setValue(InternalFieldKey.HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR, 
					SysCode.HOST_REQ_MANUAL_AUTHORIZATION);

			// mapHost_header - AUTHORIZATION JUDGMENT DIVISION ('4'(Acquiring))
			internalMsg.setValue(InternalFieldKey.HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION, 
					SysCode.HOST_AUTH_JUDG_DIV_NONAEON);

            /*
             *  [emaranan 12-14-2011] If F39 is present, 
             *      set CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
             *      else no need to set any value.
             */
            if (isoMsg.hasField(39)) {
                String fepCode;
                internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, isoMsg.getString(39));
                fepCode = changeToFEPCode(Constants.OUTRESCNV_NW_CODE_BANKNET, isoMsg.getString(39));
                internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepCode); 
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
                        "[BanknetCommand] echo_test_ac_response : fep response code : " + fepCode);
            } // End of IF statement
            
			// update FepMessage
			fepMsg.setMessageContent(internalMsg);

		} catch (Throwable th) {			
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		/*
		 * Output the transaction history log
		 * (set "echo_test" into PAN field for the display in transaction history)
		 */
		internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_SUCCESS_ECHOTEST_BNK);

	    // Set Source ID
		internalMsg.setValue(HOST_HEADER.SOURCE_ID, OUTRESCNV_NW_CODE_BANKNET);
		internalMsg.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);
   
		// update FepMessage
		fepMsgFromSharedMemory.setMessageContent(internalMsg);

		/*
		 * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsgFromSharedMemory);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of echo_test_ac_response() method

	/**
	 * Execute file update acquiring request message.
	 * @param fepMsg The Fep Message to execute where the message content is InternalFormat (DFI: 01)
	 * @throws SQLException 
	 * @throws ISOException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @since 0.0.1
	 */
	private void fileUpdate_ac_request(FepMessage fepMsg) 
	     throws SQLException, ISOException, AeonDBException, IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] fileUpdate_ac_request() start");

		ISOMsg isoMsg = null;
		String sysMsgID;
		byte[] encodedMsg;
		String msgProcessingNo;
		FEPT101FileupdateaqtblDomain domain;
		String networkKey;
		String busDate;

		try {
			// Call edit_0302_message() method
			isoMsg = this.edit_0302_message(fepMsg);

		} catch (Throwable th) {
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2131, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		// Encode the message.
		encodedMsg = pack(isoMsg);
        
        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BankentCommand] Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
        			"[BankentCommand] file_update_ac_request() End Process");
                    return;
        } // End of if bytesMessage is 0 
        
		
		/* [MQuines: 10/03/2011] Create new InternalFormat
         * [MQuines: 11/21/2011] Modified implementation from InternalFormat inFormat = new InternalFormat()
         * to InternalFormat inFormat = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg)
         */
		InternalFormat inFormat = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
        try {
			inFormat.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), PROCESS_I_O_TYPE_ACREQ);
            inFormat.setValue(HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_HST);
		} catch (Exception e) {
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
            		"[BanknetCommand] signon_off_ac_request : Exception Occured: "
            		+ FepUtilities.getCustomStackTrace(e));
        }

		/* Write the Shared Memory(Internal format)
		 * Get the message FEP sequence No.
		 */
		fepMsg.setMessageContent(inFormat);
		msgProcessingNo = fepMsg.getFEPSeq();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand]: fileUpdate_ac_request() : msgProcessingNo = " + msgProcessingNo);
		
		// Call super.setTransactionMessage() method
		setTransactionMessage(msgProcessingNo, timeoutValue, fepMsg);

		/*
		 * Transmit the Request Message
		 * Edit the queue header
		 * Processing type identifier = "03" Data format identifier = "10"
		 * set encoded message
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
		fepMsg.setMessageContent(encodedMsg);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of LCP.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),refer to
		 * "SS0111 System Code Definition(FEP).xls"
		 */
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving message from <" + 
		mcpProcess.getQueueName(SysCode.LN_MON_MCP) + ">");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <" + 
				mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process Type: " + fepMsg.getProcessingTypeIdentifier() + 
				"| Data Format: " + fepMsg.getDataFormatIdentifier() +
				"| MTI: " + isoMsg.getMTI() + 
				"| fepSeq: " + fepMsg.getFEPSeq() + 
				"| Network ID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response COde: " + (
						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
		/*
		 * Insert Fileupdate Acquiring Log Table
		 * Net Work Key = "BKN" + MTI + PAN + Systems Trace Audit Number(STAN)
		 */
		domain = new FEPT101FileupdateaqtblDomain();
		networkKey = SysCode.BKN + isoMsg.getMTI() + isoMsg.getString(2) + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] fileUpdate_ac_request : Network Key : " + networkKey);
		domain.setNw_key(networkKey);
		domain.setFep_seq(new BigDecimal(msgProcessingNo));
		domain.setUpdateId(mcpProcess.getProcessName());
		busDate = fepMsg.getDateTime().substring(0, 8);
		domain.setBus_date(busDate);
		
		try {
			fileupdateAcDao.insert(domain);
			transactionMgr.commit();
			
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
			transactionMgr.rollback();
			return;
		}
	} // End of fileUpdate_ac_request() method

	/**
	 * Execute file update acquiring response message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws SQLException 
	 * @throws ISOException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void fileUpdate_ac_response(FepMessage fepMsg) throws SQLException,
	ISOException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] fileUpdate_ac_response start");

		/* Check the message format according to the MTI
		 * create ISOMsg from FepMessage
		 */
		ISOMsg isoMsg = fepMsg.getMessageContent();
		InternalFormat internalFormatErr;
		String sysMsgID;
		String networkKey = SysCode.BKN + SysCode.MTI_0302 + isoMsg.getString(2) + isoMsg.getString(11);
		FEPT101FileupdateaqtblDomain domain = new FEPT101FileupdateaqtblDomain();
		domain.setNw_key(networkKey);
		List<FEPT101FileupdateaqtblDomain> result;
		String msgSeqNum;
		FepMessage fepMsgFromSharedMemory;
		InternalFormat internalMsg;
		
		// validate ISOMsg
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			// If error occurs output the System Log
			sysMsgID = this.mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, CLIENT_SYSLOG_SCREEN_2128, null);

			// create internal format
			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ACRES);
			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 * (set "fileUpdate error" into PAN field for the display in
			 * transaction history)
			 * 
			 * [mqueja:20112211] Added setting of Transaction Code
			 */
	        if (isoMsg.getString(91).equals(FLD91_FILE_UPDATE_CODE_1)) {      	
	        	internalFormatErr.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, TRANS_CODE_051301);
	        	
	        } else if (isoMsg.getString(91).equals(FLD91_FILE_UPDATE_CODE_2)) {
	        	internalFormatErr.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, TRANS_CODE_051302);
	        	
	        } else if (isoMsg.getString(91).equals(FLD91_FILE_UPDATE_CODE_3)) {
	        	internalFormatErr.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, TRANS_CODE_051303);
	        	
	        } else if (isoMsg.getString(91).equals(FLD91_FILE_UPDATE_CODE_5)) {
	        	internalFormatErr.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, TRANS_CODE_051304);
	        }// End of Field91 value checking
	        
	        // [JMarigondon 11-22-2011] Replace constant value for SourceID
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN,
					Constants.MSG_ERROR_FILEUPDATE_BNK);
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, 
					Constants.OUTRESCNV_NW_CODE_HST);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			// End the process
			return;
		}

		/*
		 * get Fep Sequence by searching Fileupdate Acquiring Log Table for the following use
		 * Net Work Key = "BKN" + MTI + PAN + Systems Trace Audit Number(STAN)
		 */
		networkKey = SysCode.BKN + SysCode.MTI_0302 + isoMsg.getString(2) + isoMsg.getString(11);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] fileUpdate_ac_response : Network Key : " + networkKey);
		domain = new FEPT101FileupdateaqtblDomain();
		domain.setNw_key(networkKey);
		result = null;
		
		try {
			result = fileupdateAcDao.findByCondition(domain);
			
		} catch (Exception ex) {
			args[0]  = ex.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute(): End Process");
			transactionMgr.rollback();
			return;
		}

		if (null == result || result.isEmpty()) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] fileUpdate_ac_response : no record found)");
			return;
		}

		/*
		 * Get the original message
		 * Get the message FEP sequence No
		 */
		msgSeqNum = ISOUtil.padleft(result.get(0).getFep_seq() + NO_SPACE, 7, Constants.cPAD_0);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] fileUpdate_ac_response : msgSeqNum = " + msgSeqNum);

		// Call super.getTransactionMessage() method
		fepMsgFromSharedMemory = getTransactionMessage(msgSeqNum);
        // [MAguila: 20120619] Redmine #3860 Updated Logging
		if(null == fepMsgFromSharedMemory) {
			args[0] = msgSeqNum;
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205, args);              
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] : sharedFepMessage is null. Return from execute()");
			return;
		}
        
		internalMsg = fepMsgFromSharedMemory.getMessageContent();

		try {
			/*
			 * > Update Internal Format according to the ISOMsg
			 * mapControl_info_part - Network Authorization Result 
			 * ( If Field 39 is in ("00', '08', '10', '87') set '1'(Appove) 
			 * else set Constants.cPAD_0 (Decline) )
			 * 
			 * mapControl_info_part - Capture card flag (Edit according to
			 * response code)
			 */
			internalMsg.setValue(InternalFieldKey.CONTROL_INFORMATION.CAPTURE_CARD_FLAG, 
					(Constants.FLD39_RESPONSE_CODE_CAPTURED_CARD.equals(isoMsg.getString(39)) 
							? "1" 
							: "0"));

			// mapHost_header - REQUEST/RESPONSE INDICATOR (Set '2')
			internalMsg.setValue(InternalFieldKey.HOST_HEADER
					.REQUEST_OR_RESPONSE_INDICATOR, SysCode.HOST_REQ_MANUAL_AUTHORIZATION);

			// mapHost_header - AUTHORIZATION JUDGMENT DIVISION ('4'(Acquiring))
			internalMsg.setValue(InternalFieldKey.HOST_HEADER
					.AUTHORIZATION_JUDGMENT_DIVISION, SysCode.HOST_REQUEST_HSM_RELATE);

			/* 
			 * Issuer File Update when Acquiring response *2
			 * mapHost_NW_Pec_Data - Forwarding Institution Identification Code
			 */
			internalMsg.setValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey
					.FORWARDING_INSTITUTION_IDENTIFICATION_CODE, isoMsg.getString(33));
            
            /*
             *  [emaranan 12-14-2011] If F39 is present, 
             *      set CONTROL_INFORMATION.FEP_RESPONSE_CODE, 
             *      else no need to set any value.
             */
            if (isoMsg.hasField(39)) {
                String fepCode;
                internalMsg.setValue(
                		IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.RESPONSE_CODE, isoMsg.getString(39));
                internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, isoMsg.getString(39));
                fepCode = changeToFEPCode(Constants.OUTRESCNV_NW_CODE_BANKNET, isoMsg.getString(39));
                internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, fepCode); 
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
                        "[BanknetCommand] fileUpdate_ac_response : fepCode : " + fepCode);
            } // End of IF statement

			/* 
			 * Issuer File Update when Acquiring response *2
			 * mapHost_NW_Pec_Data - Network Data
			 */
			internalMsg.setValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.NETWORK_DATA,
					isoMsg.getString(63));

			/* 
			 * Issuer File Update when Acquiring response *2
			 * mapHost_NW_Pec_Data - Additional Response Data
			 */
			internalMsg.setValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.ADDITIONAL_RESPONSE_DATA, 
					isoMsg.getString(44));

			/*
			 * Issuer File Update Transmission Date and Time
			 * [Megan Quines: 08/30/2010] [Redmine #977]
			 * [Convert Transmission Date and TimeTransmission Date and Time into GMT]
			 */
			internalMsg.setValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.TRANSMISSION_DATE_AND_TIME, 
					isoMsg.getString(7));
			
			// [JMarigondon 11-22-2011] Add setting of Source ID
			internalMsg.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, 
					Constants.OUTRESCNV_NW_CODE_HST);
			
			// [JMarigondon 11-22-2011] Add setting of Timestamp for Monitoring System
			internalMsg.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ACRES);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute(): End Process");
			return;
		}
		/*
		 * [mqueja:20111121] Updated PAN Error Message
		 * Set REQUEST_OR_RESPONSE_INDICATOR in HOST_HEADER
		 * Set PAN as "file update"
		 */
		internalMsg.setValue(HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR, HOST_RESPONSE);

		/*
		 * update FepMessage
		 * Edit the queue header
		 * Processing type identifier = "22" Data format identifier = "01"
		 */
		fepMsgFromSharedMemory.setMessageContent(internalMsg);
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_RES_PROC);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of FEP processing function.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_FEP_MCP),refer to
		 * "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMsgFromSharedMemory);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
				"[Process type:" + fepMsgFromSharedMemory.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsgFromSharedMemory.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsgFromSharedMemory.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		/*
		 * [JMarigondon 11-22-2011] Send message to MONSTORE
		 * 
		 * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsgFromSharedMemory.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsgFromSharedMemory.getDataFormatIdentifier() +
				"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsgFromSharedMemory.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalMsg.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of fileUpdate_ac_response() method

	/**
	 * Execute the PIN Encryption Key Change Request message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws ISOException 
	 * @throws HSMException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void key_change_is(FepMessage fepMsg) throws ISOException, HSMException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand]: key_change_is() start");

		InternalFormat internalFormatOk = null;

		/*
		 * Check the message format according to the MTI
		 * create ISOMsg from FepMessage
		 */
		ISOMsg isoMsg = fepMsg.getMessageContent();
		String sysMsgID;
		InternalFormat internalFormatErr;
		InternalFormat internalFormatErrNew;
		String field7Sub1yyyymmdd;
		String field7Sub2time;
		HSMCommandA6 hsmCommandA6;
		ISOMsg isoMsgResponse;
		byte[] encodedMsg;
		ISOMsg subIsoMsg;

		// validate ISOMsg
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {
			// If error occurs output the System Log
			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);

			// create internal format
			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 * (set "key_change error" into PAN field for the display in transaction history)
			 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_KEYEXCHANGE_BNK);
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000005);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			// Edit the Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode( BANKNET)
            byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
            			"[BanknetCommand]Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] key_change_is(): End Process");
                return;
            } // End of if bytesMessage is 0 
            
            fepMsg.setMessageContent(messageArray);

            // Processing type identifier = "04" 
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
            
            // Data format identifier = "10"
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

			// Transmit Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");
			// End the process
			return;
		}

		// Create the Internal Format according to the ISOMsg
		try {
			internalFormatOk = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatOk.addTimestamp(mcpProcess.getProcessName(),  mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISRES);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			
			// create internal format, copy isoMsg fields to internal format
			internalFormatErrNew = new InternalFormat();
			internalFormatErrNew.addTimestamp(mcpProcess.getProcessName(),  mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
			
			//MTI
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE, SysCode.MTI_0800);
			
			// (7) Transmission Date and Time
			field7Sub1yyyymmdd = ISODate.formatDate(
					Calendar.getInstance().getTime(), Constants.DATE_FORMAT_yyyy) + isoMsg.getString(7).substring(0, 4);
			field7Sub2time = isoMsg.getString(7).substring(4, 10);
			
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_DATE, field7Sub1yyyymmdd);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_TIME, field7Sub2time);
			
			// (11) Systems Trace Audit Number
			internalFormatErrNew.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER, isoMsg.getString(11));
			
			// (33) Forwarding Institution Identification Code
			internalFormatErrNew.setValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE, isoMsg.getString(33));
			
			// (63) Network Data
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA, isoMsg.getString(63));
			
			// (70) Network Management Information Code
			internalFormatErrNew.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000005);

			/*
			 * Output the Transaction History Log(Only the header of Internal format)
			 * (set "key_change error" into PAN field for the display in transaction history)
			 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
			 */
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_KEYEXCHANGE_BNK);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErrNew);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErrNew.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErrNew.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErrNew.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErrNew.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErrNew.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// Edit the Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode( BANKNET)
            byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
            			"[BanknetCommand]Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] key_change_is(): End Process");
                return;
            } // End of if bytesMessage is 0 
            
			fepMsg.setMessageContent(messageArray);
            
			// Processing type identifier = "04" 
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
            
            // Data format identifier = "10"
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

			// Transmit the Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");

			// > End the process
			return;
		}

		/*
		 * Key Exchange
		 * Call sendHSMMsg() method of HSM Common Access to send
		 * HSMCommandA6 command
		 *  Refer to "SS0106 Message Interface Item Editing
		 * Definition(FEXC001 FINF407 Internal A6 Command Interface).xls"
		 * [Feona May Samson: 07/08/2010] [Redmine #405] [how to get the instance of HSMCommandA7]
		 *  Get the new KEY from HSMCommandA7 command
		 *  Refer to sheet FINF408 in "SS0106 Message Interface Item
		 * Editing Definition (FINH002 FINF402 Internal A1 Response Interface ).xls" of HSM
		 */
		hsmCommandA6 = new HSMCommandA6();

		// set Key type
		hsmCommandA6.setKey_type(SysCode.HSM_KEY_TYPE_ZPK);

		/*
		 * set ZMK
		 * Key Name of the Issuing Encryption information in the BANKNET MCP
		 * configuration file
		 */
		hsmCommandA6.setZMK_Name(isKeyName);

		/*
		 * set Key (ZMK)
		 * Subfield 4 of Field48.11 acquired from BANKNET network
		 * check field 48 if exist
		 */
		if (isoMsg.hasField(48)) {

			// cast field 48 value to ISOMsg 
			subIsoMsg = (ISOMsg) isoMsg.getValue(48);

			// check field 11 if exist
			if (subIsoMsg.hasField(11)) {

				// set Key (ZMK) with subfield 4
				hsmCommandA6.setKey_ZMK(subIsoMsg.getString(11).substring(7,23));
			}
		}

		/* 
		 * set Key Scheme (LMK)
		 * set Atalla Variant 
		 */
		hsmCommandA6.setKey_Scheme_LMK(SysCode.HSM_KEY_SCHEME_SINGLE);
		hsmCommandA6.setAtalla_Variant("-");

		/* 
		 * set strKeyName
		 * Key Name of the Issuing Encryption information in the BANKNET MCP configuration file 
		 */
		hsmCommandA6.setStrKeyName(isKeyName);

		/*
		 * call sendHSMMsg() method
		 * Call edit_0810_message() method
		 */
		hsmThalesAccess.sendHSMMsg(hsmCommandA6);

		isoMsgResponse = this.edit_0810_message(fepMsg);

		// Encode the ISOMsg
		encodedMsg = pack(isoMsgResponse);

        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand]Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] key_change_is(): End Process");
            return;
        } // End of if bytesMessage is 0 
        
		/* 
		 * Output the transaction history log
		 * (set "key_change" into PAN field for the display in transaction history)
		 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code] 
		 */
		internalFormatOk.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_SUCCESS_KEYEXCHANGE_BNK);
		internalFormatOk.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
		internalFormatOk.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, 
				Constants.TRANS_CODE_000005);

		/* 
		 * set updated internal format
		 * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsg.setMessageContent(internalFormatOk);
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/* 
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormatOk.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormatOk.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormatOk.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormatOk.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| Response Code: "+ internalFormatOk.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		/*
		 * Transmit the Response Message
		 * Edit the queue header
		 * Processing type identifier = "04" Data format identifier = "10"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

		// set encoded message
		fepMsg.setMessageContent(encodedMsg);

		/*
		 *  Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of LCP
		 *  *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),refer to
		 * "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + isoMsg.getMTI() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response Code: "+ isoMsg.getString(39) +"]");
	} // End of key_change_is() method

	/**
	 * Execute the echo-test issuing request message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws ISOException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void echo_test_is(FepMessage fepMsg) throws ISOException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] echo_test_is() start");

		InternalFormat internalFormatOk = null;
		ISOMsg isoMsg;
		String[] args = null;
		String sysMsgID;
		InternalFormat internalFormatErr;
		InternalFormat internalFormatErrNew;
		String field7Sub1yyyymmdd;
		String field7Sub2time;
		ISOMsg isoMsgResponse;
		byte[] encodedMsg;

		/*
		 * Check the message format according to the MTI
		 * create ISOMsg from FepMessage
		 */
		isoMsg = fepMsg.getMessageContent();

		// validate ISOMsg
		if (validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			// If error occurs output the System Log
			args[0] = Constants.LOG_CLASS_ECHO_TEST_IS;
			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, 
					Constants.CLIENT_SYSLOG_SCREEN_2128, args);

			// create internal format
			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(),
					Constants.PROCESS_I_O_TYPE_ISREQ);
			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 * (set "echo_test error" into PAN field for the display in transaction history)
			 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_ERROR_ECHOTEST_BNK);
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message)
			 * to send the message to the queue of Monitor 
			 * STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer to "SS0111 System Code 
			 * Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// Edit the Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode( BANKNET)
            byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] echo_test_is() End Process");
                return;
            } // End of if bytesMessage is 0 
            
            fepMsg.setMessageContent(messageArray);
            
			// Processing type identifier = "04" 
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
            
            // Data format identifier = "10"
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

			// Transmit Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");

			// End the process
			return;
		}

		try {
			// Create the Internal Format according to the ISOMsg
			internalFormatOk = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatOk.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISRES);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			
			// create internal format, copy isoMsg fields to internal format
			internalFormatErrNew = new InternalFormat();
			internalFormatErrNew.addTimestamp(mcpProcess.getProcessName(),  mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
			
			//MTI
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE, SysCode.MTI_0800);
			
			// (7) Transmission Date and Time
			field7Sub1yyyymmdd = ISODate.formatDate(Calendar.getInstance().getTime(),
					Constants.DATE_FORMAT_yyyy) + isoMsg.getString(7).substring(0, 4);
			field7Sub2time = isoMsg.getString(7).substring(4, 10);
			
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_DATE, field7Sub1yyyymmdd);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_TIME, field7Sub2time);
			
			// (11) Systems Trace Audit Number
			internalFormatErrNew.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER, isoMsg.getString(11));
			
			// (33) Forwarding Institution Identification Code
			internalFormatErrNew.setValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE, isoMsg.getString(33));
			
			// (63) Network Data
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA, isoMsg.getString(63));
			
			// (70) Network Management Information Code
			internalFormatErrNew.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);

			/*
			 * Output the Transaction History Log(Only the header of Internal format)
			 * (set "echo_test error" into PAN field for the display in transaction history)
			 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
			 */
			internalFormatErrNew.setValue(
					InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_ERROR_ECHOTEST_BNK);
			internalFormatErrNew.setValue(
					InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			internalFormatErrNew.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);
			fepMsg.setMessageContent(internalFormatErrNew);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErrNew.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErrNew.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErrNew.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErrNew.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErrNew.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// Edit the Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode( BANKNET)
            byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand]Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] echo_test_is() End Process");
                return;
            } // End of if bytesMessage is 0 
            
			fepMsg.setMessageContent(messageArray);
			
			/*
			 * Processing type identifier = "04"
			 * Data format identifier = "10"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

			// Transmit the Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");
			// End the process
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		// Call edit_0810_message() method
		isoMsgResponse = this.edit_0810_message(fepMsg);

		// Encode the ISOMsg
		encodedMsg = pack(isoMsgResponse);

        // If error occurs,
        if(encodedMsg.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] Error in Packing Message");
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
            		"[BanknetCommand] echo_test_is() End Process");
            return;
        } 
        
		/*
		 * Output the transaction history log
		 * (set "echo_test" into PAN field for the display in transaction history)
		 */
		internalFormatOk.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, Constants.MSG_SUCCESS_ECHOTEST_BNK);

		/*
		 * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * set updated internal format
		 * [mqueja:8/18/2011] [added setting of sourceId and trasnsaction code]
		 */
		internalFormatOk.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
		internalFormatOk.setValue(
				InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000009);
		fepMsg.setMessageContent(internalFormatOk);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormatOk.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormatOk.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormatOk.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormatOk.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalFormatOk.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

		/*
		 * Transmit the Response Message
		 * Edit the queue header
		 * Processing type identifier = "04" Data format identifier = "10"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

		// set encoded message
		fepMsg.setMessageContent(encodedMsg);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the message to the queue of LCP.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),
		 *  refer to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + isoMsg.getMTI() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response Code: "+ isoMsg.getString(39) +"]");
	} // End of echo_test_is() method

	/**
	 * Execute the Management Notification Request message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg(DFI: 10)
	 * @throws ISOException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void management_notification_is_request(FepMessage fepMsg)
	throws ISOException, AeonDBException, IllegalParamException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] management_notification_is_request() start");

		InternalFormat internalFormatOk = null;
		ISOMsg isoMsg;
		String sysMsgID;
		InternalFormat internalFormatErr;
		InternalFormat internalFormatErrNew;
		String field7Sub1yyyymmdd;
		String field7Sub2time;
		int[] unsetVariableIsRequest = {48, 60, 120};

		// Check the message format according to the MTI, get ISOMsg from FepMessage
		isoMsg = fepMsg.getMessageContent();

		// validate ISOMsg
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			// If error occurs output the System Log
			sysMsgID = mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);

			// create internal format
			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
			/*
			 * Output the Transaction History Log(Output the Transaction History Log)
			 * (set "notification error" into PAN field for the display in transaction history)
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_ADMIN_BNK);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_070801);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName,
			 * FepMessage message) to send the message to the queue of Monitor 
			 * STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer to "SS0111 System Code 
			 * Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// Edit Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.unset(unsetVariableIsRequest);
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode(BANKNET)
           byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand]Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] management_notification_is_request() End Process");
                return;
            } // End of if bytesMessage is 0 
            
			fepMsg.setMessageContent(messageArray);
			
			/*
			 * Processing type identifier = "21"
			 * Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_REQ_PROC);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			// Transmit Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");
			
			// End the process
			return;
		}

		try {
			// Create the Internal Format according to the ISOMsg
			internalFormatOk = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatOk.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
		
		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			
			// create internal format, copy isoMsg fields to internal format
			internalFormatErrNew = new InternalFormat();
			internalFormatErrNew.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISRES);
			
			//MTI
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE, SysCode.MTI_0620);
			
			// (7) Transmission Date and Time
			field7Sub1yyyymmdd = ISODate.formatDate(Calendar.getInstance().getTime(),
					Constants.DATE_FORMAT_yyyy) + isoMsg.getString(7).substring(0, 4);
			field7Sub2time = isoMsg.getString(7).substring(4, 10);
			
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_DATE, field7Sub1yyyymmdd);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_TIME, field7Sub2time);
			
			// (11) Systems Trace Audit Number
			internalFormatErrNew.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER, isoMsg.getString(11));
			
			// (33) Forwarding Institution Identification Code
			internalFormatErrNew.setValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE, isoMsg.getString(33));
			
			// (48) Additional Data - Private
			internalFormatErrNew.setValue(OPTIONAL_INFORMATION.ADDITION_DATA, isoMsg.getString(48));
			
			// (60) Advice Reason Code
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.ADVICE_REASON_CODE, isoMsg.getString(60));
			
			/*
			 * [Feona May Samson: 07/12/2010] [Redmine #589]
			 * [(62) Intermediate Network Facility(INF) Data, 63) Network Data]
			 */
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA, isoMsg.getString(63));

			/*
			 * [Feona May Samson: 07/12/2010] [Redmine #589]
			 * [(100) Receiving Institution Identification Code, (120) Record Data]
			 * 
			 * Output the Transaction History Log(Only the header of Internal format)
			 * (set "notification error" into PAN field for the display in transaction history)
			 */
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_ADMIN_BNK);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, 
					Constants.TRANS_CODE_070801);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErrNew);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErrNew.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErrNew.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErrNew.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErrNew.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErrNew.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// Edit the Response Message(Decline)
			isoMsg.setResponseMTI();
			isoMsg.unset(unsetVariableIsRequest);
			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);

			// Encode( BANKNET)
           byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand]Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] management_notification_is() End Process");
                return;
            } // End of if bytesMessage is 0 
            
			fepMsg.setMessageContent(messageArray); 
			
			/*
			 * Processing type identifier = "21" 
			 * Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_REQ_PROC);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			// Transmit the Response Message
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");
			// End the process
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		/*
		 * Write the Shared Memory(ISOMsg)
		 * Call getSequence() of IMCPProcess to create the FEP sequence No
		 */
		fepMsg.setFEPSeq(ISOUtil.padleft(mcpProcess.getSequence(SysCode.FEP_SEQUENCE), 7, Constants.cPAD_0));
		String fepSeqNo = fepMsg.getFEPSeq();
		internalFormatOk.setValue(HOST_HEADER.MESSAGE_PROCESSING_NUMBER, fepSeqNo); 
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] management_notification_is_request : fepSeqNo = " + fepSeqNo);

		// Call super.setTransactionMessage() method, write the message into Shared Memory
		setTransactionMessage(fepSeqNo, timeoutValue, fepMsg);

		/*
		 * Send the Internal Format to the FEP processing function
		 * Edit the queue header
		 * Processing type identifier = "21" Data format identifier = "01"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_REQ_PROC);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		// set internal format to message content
		fepMsg.setMessageContent(internalFormatOk);
		
		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of FEP processing function.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_FEP_MCP),
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_FEP_MCP), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_FEP_MCP) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormatOk.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormatOk.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormatOk.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormatOk.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalFormatOk.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of management_notification_is_request() method

	/**
	 * Execute Management Notification Response message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg (DFI: 10)
	 * @throws SQLException 
	 * @throws ISOException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void management_notification_is_response(FepMessage fepMsg)
	throws SQLException, ISOException, SharedMemoryException {
		mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] management_notification_is_response() start");
		
		String msgSeqNum;
		FepMessage fepMsgFromSharedMemory;
		ISOMsg isoMsg;
		InternalFormat internalMsg;
		FEPT022BnkisDomain domain;
		String networkKey;

		/*
		 * Get the ISOMsg from the shared memory
		 * Get the message FEP sequence No
		 */
		msgSeqNum = fepMsg.getFEPSeq();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] management_notification_is_response :  message Sequence Number : " + msgSeqNum);

		// Call super.getTransactionMessage() method.
		fepMsgFromSharedMemory = getTransactionMessage(msgSeqNum);
        // [MAguila: 20120619] Redmine #3860 Updated Logging
		if(null == fepMsgFromSharedMemory) {
			args[0] = msgSeqNum;
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_WARNING, SERVER_APPLOG_INFO_SAW2205, args);         
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] management_notification_is_response : sharedFepMessage is null");
			return;
		}
        
		isoMsg = null;
		if (fepMsgFromSharedMemory != null) {
			isoMsg = fepMsgFromSharedMemory.getMessageContent();
		}

		// If return is null
		if (null == isoMsg) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetCommand] management_notification_is_response : shared memory message is null");
			// End the process
			return;
		}

		// Update ISOMsg according to the internal Format
		try {
			// Call edit_0630_message method
			isoMsg = this.edit_0630_message(fepMsgFromSharedMemory);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			
			// retrieve internal format message
			internalMsg = fepMsg.getMessageContent();
			internalMsg.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISRES);
			/*
			 * Update the Log Issuing Table
			 * Call FEPT022BnkisDAO.insert() to insert the message
			 * *the DAO name is Table ID+Table(abbreviation) +"DAO",Table ID
			 * and Table(abbreviation), refers to [BD Document][FEP] 7-1 Logical Table List.xls
			 * 
			 * [Feona May Samson: 06/04/2010] [Redmine #407] [refer to #403]
			 * create domain for insert
			 */
			domain = new FEPT022BnkisDomain();

			// create network key
			networkKey = internalMsg.getValue(
					InternalFieldKey.OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_COMMON_DATA.PAN).getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_AMOUNT).getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_DATE_TIME) .getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_HEADER.ORIGINAL_MESSAGE_PROCESSING_NUMBER).getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_COMMON_DATA.CARD_ACCEPTOR_TERMINAL_ID).getValue()
					+ internalMsg.getValue(
							InternalFieldKey.HOST_COMMON_DATA.CARD_ACCEPTOR_IDENTIFICATION_CODE).getValue();

			// set domain Net Work Key
			domain.setNw_key(networkKey);

			// set FEP Sequence
			domain.setFep_seq(new BigDecimal(fepMsg.getFEPSeq()));

			// set Business Date YYYYMMDD
			domain.setBus_date(fepMsg.getDateTime().substring(0,7));

            byte[] messageArray = pack(isoMsg);
            
            // If error occurs,
            if(messageArray.length < 1) {
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] Error in Packing Message");
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                		"[BanknetCommand] management_notification_is_response() End Process");
                return;
            } // End of if bytesMessage is 0
            
			// set Message Data
			domain.setMsg_data(messageArray);

			/*
			 * [Feona May Samson: 07/20/2010]
			 * [Redmine #649] [Exception in thread "main" java.sql.SQLException]
			 */
			domain.setUpdateId(mcpProcess.getProcessName());

			// [Feona May Samson: 06/18/2010] [Redmine #423] ["2.Update the Log Issuing Table."]
			domain.setPro_status(Constants.COL_PROCESS_STATUS_APPROVED); // if issuer = approve
			if (FLD39_RESPONSE_CODE_SUCCESS.equals(isoMsg.getString(39))) {
				domain.setPro_result(Constants.COL_PROCESS_RESULT_APPROVE);
				
			} else {
				domain.setPro_result(Constants.COL_PROCESS_RESULT_DECLINE);
			}

			try {
				dao.insert(domain);
				transactionMgr.commit();
				
			} catch (Exception ex) {
				args[0]  = ex.getMessage();
				sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB);
				mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_FIND_DB, args);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(ex));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] execute() End Process");
				transactionMgr.rollback();
				return;
			}

			/*
			 * Output the Transaction History Log(Only the header of Internal format)
			 * (set "notification error" into PAN field for the display in  transaction history)
			 */
			internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_ADMIN_BNK);
			fepMsg.setMessageContent(internalMsg);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalMsg.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalMsg.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			
			/**[rrabaya 09112014] RM#5392 HOST Timeout Due to 0620 Administrative Advice From MasterCard : Start*/
			//Comment: will not send 0620 to HOSTSAF anymore
//			/*
//			 * HOST SAF Table Transmission(Reversal).
//			 * Call sendMessage(String queueName, FepMessage message) to send
//			 * the message to the queue of HOST STORE process.
//			 * *queueName=mcpProcess.getQueueName(SysCode.LN_HOST_STORE),refer
//			 * to "SS0111 System Code Definition(FEP).xls"
//			 */
//			sendMessage(mcpProcess.getQueueName(SysCode.LN_HOST_STORE), fepMsg);
//			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
//					+ mcpProcess.getQueueName(SysCode.LN_HOST_STORE) + ">" +
//					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
//					"| Data format:" + fepMsg.getDataFormatIdentifier() +
//					"| MTI:" + internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
//					"| FepSeq:" + fepMsg.getFEPSeq() +
//					"| NetworkID: BNK" + 
//					"| Transaction Code(service):" + internalMsg.getValue(
//						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
//					"| SourceID: " + internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue() +
//					"| Destination ID: " + internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
//					"| FEP Response Code: "+ internalMsg.getValue(
//							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
//			
//
//			// Edit the Response Message(Decline)
//			isoMsg.setResponseMTI();
//			isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_INVALID_TRANSACTION);
//
//			// Encode( BANKNET)
//            byte[] messageArrayBnkLcp = pack(isoMsg);
//            
//            // If error occurs,
//            if(messageArrayBnkLcp.length < 1) {
//            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand]Error in Packing Message");
//            	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
//            			"[BanknetCommand] management_notification_is_response() End Process");
//            	return;
//            } // End of if bytesMessage is 0 
//            
//			fepMsg.setMessageContent(messageArrayBnkLcp);
//
//			/*
//			 * Transmit the Response Message
//			 * Edit the queue header
//			 * Processing type identifier = "04" Data format identifier = "10"
//			 */
//			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
//			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);
			/**[rrabaya 09112014] RM#5392 HOST Timeout Due to 0620 Administrative Advice From MasterCard : End*/

			/*
			 * Call sendMessage(String queueName, FepMessage message) 
			 * to send the message to the queue of LCP.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),refer to
			 * "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + isoMsg.getMTI() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Processing Code(first 2 digits): " + (
							(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
					"| Response Code: "+ isoMsg.getString(39) +"]");
			// End the process
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute() End Process");
			return;
		}

		InternalFormat internalFormat = fepMsg.getMessageContent(); 
	    internalFormat.setValue(HOST_COMMON_DATA.PAN, MSG_SUCCESS_ADMIN_BNK);
	    /*
	     * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
		 * to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormat.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormat.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormat.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormat.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalFormat.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
		
		// Encode the message.
        byte[] messageArray = pack(isoMsg);
        
        // If error occurs,
        if(messageArray.length < 1) {
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, "[BanknetCommand] Error in Packing Message");
        	mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, 
        			"[BanknetCommand] management_notification_is_response() End Process");
        	return;
        } // End of if bytesMessage is 0 
        
		fepMsgFromSharedMemory.setMessageContent(messageArray);

		/*
		 * Transmit the Request Message
		 * Edit the queue header
		 * Processing type identifier = "04" Data format identifier = "10"
		 */
		fepMsgFromSharedMemory.setProcessingTypeIdentifier(SysCode.QH_PTI_MCP_RES);
		fepMsgFromSharedMemory.setDataFormatIdentifier(SysCode.QH_DFI_ORG_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send the message to the queue of LCP.
		 * queueName=mcpProcess.getQueueName(SysCode.LN_BNK_LCP),
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 */
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : Start*/
		sendMessage(mcpProcess.getQueueName(SysCode.LN_BNK_LCP), fepMsgFromSharedMemory);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "receiving message from <" +
		mcpProcess.getQueueName(SysCode.LN_MON_MCP) + ">");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION, "sending a message to <" + 
				mcpProcess.getQueueName(SysCode.LN_BNK_LCP) + ">" +
				"[Process Type: " + fepMsg.getProcessingTypeIdentifier() + 
				"| Data Format: " + fepMsg.getDataFormatIdentifier() +
				"| MTI: " + isoMsg.getMTI() + 
				"| fepSeq: " + fepMsg.getFEPSeq() + 
				"| Network ID: BNK" + 
				"| Processing Code(first 2 digits): " + (
						(isoMsg.hasField(3))?(isoMsg.getString(3).substring(0, 2)) : "Not Present") +
				"| Response COde: " + (
						(isoMsg.hasField(39)) ? isoMsg.getString(39) : "Not Present") + "]");
		/**[rrabaya 09242014] RM# 5745 [Mastercard] - Incorrect queue name in BANKNETMCP INFO logs : End*/
	} // End of management_notification_is_response() method

	/**
	 * Execute PIN Encryption Key Reflection Notification issuing request
	 * message.
	 * @param fepMsg The Fep Message to execute where the message content is ISOMsg(DFI: 10)
	 * @throws HSMException 
	 * @throws SharedMemoryException 
	 * @since 0.0.1
	 */
	private void key_reflection_notification_is(FepMessage fepMsg)
	          throws HSMException, SharedMemoryException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] key_reflection_notification_is() start");

		InternalFormat internalFormatOk = null;
		ISOMsg isoMsg;
		HSMCommandKE commandKE;
		String sysMsgID;
		InternalFormat internalFormatErrNew;
		String field7Sub1yyyymmdd;
		String field7Sub2time;
		InternalFormat internalFormatErr;

		// Check the message format according to the MTI, create ISOMsg from FepMessage
		isoMsg = fepMsg.getMessageContent();

		// validate ISOMsg
		if (this.validate(isoMsg) != Constants.VALIDATE_NO_ERROR) {

			// If error occurs output the System Log
			sysMsgID = this.mcpProcess.writeSysLog(Level.WARN, Constants.CLIENT_SYSLOG_SCREEN_2128);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_2128, null);

			// create internal format
			internalFormatErr = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatErr.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);
			/*
			 * Output the Transaction History Log(Only the header of Internal format)
			 * (set "key_reflect error" into PAN field for the display in transaction history)
			 */
			internalFormatErr.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_KEYACTIVATE_BNK);
			internalFormatErr.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000006);
			internalFormatErr.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErr);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 *  *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErr.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErr.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErr.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErr.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErr.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");

			// End the process
			return;
		}

		/*
		 * Active Key
		 * Call sendHSMMsg() method of HSM Common Access to send HSMCommandKE command
		 * Refer to "SS0106 Message Interface Item Editing Definition(FEXC001
		 * FINF458 Internal KE Command Interface).xls"
		 * If the Error Code of HSMCommandA7 command is ,set the new KEY as current KEY successfully
		 *  Refer to sheet FINF459 in "SS0106 Message Interface Item Editing
		 * Definition (FINH002 FINF402 Internal A1 Response Interface ).xls" of HSM
		 */
		commandKE = new HSMCommandKE();

		/*
		 * set ZPK
		 * Key Name of the Issuing Encryption information in the BANKNET MCP
		 * configuration file
		 */
		commandKE.setkey_type(mcpProcess.getAppParameter(Keys.XMLTags.IS_KEY_NAME).toString());

		/*
		 * call sendHSMMsg() method
		 * [Feona May Samson: 07/12/2010] [Redmine #592] [For method private key_reflection_notification_is ]
		 */
		hsmThalesAccess.sendHSMMsg(commandKE);

		try {
			// create InternalFormat from ISOMsg
			internalFormatOk = internalFormatProcess.createInternalFormatFromISOMsg(isoMsg);
			internalFormatOk.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(), 
					Constants.PROCESS_I_O_TYPE_ISREQ);

		} catch (Throwable th) {
			mcpProcess.error(th.getMessage(), th);
			args[0]  = th.getMessage();			
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130);
			mcpProcess.writeAppLog(sysMsgID, LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SCREEN_2130, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_ERROR, FepUtilities.getCustomStackTrace(th));
			
			// create internal format, copy isoMsg fields to internal format
			internalFormatErrNew = new InternalFormat();
			internalFormatErrNew.addTimestamp(mcpProcess.getProcessName(), mcpProcess.getSystime(),
					Constants.PROCESS_I_O_TYPE_ACRES);
			
			//MTI
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE, SysCode.MTI_0820);
			
			// (7) Transmission Date and Time
			field7Sub1yyyymmdd = ISODate.formatDate(Calendar.getInstance().getTime(),
					Constants.DATE_FORMAT_yyyy) + isoMsg.getString(7).substring(0, 4);
			field7Sub2time = isoMsg.getString(7).substring(4, 10);
			
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_DATE, field7Sub1yyyymmdd);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.TRANSACTION_TIME, field7Sub2time);
			
			// (11) Systems Trace Audit Number
			internalFormatErrNew.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER, isoMsg.getString(11));
			
			// (33) Forwarding Institution Identification Code
			internalFormatErrNew.setValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE, isoMsg.getString(33));
			
			// (48) Additional Data - Private
			internalFormatErrNew.setValue(OPTIONAL_INFORMATION.ADDITION_DATA, isoMsg.getString(48));
			
			// (63) Network Data
			internalFormatErrNew.setValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA, isoMsg.getString(63));
			
			// (70) Network Management Information Code
			internalFormatErrNew.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE,
					Constants.TRANS_CODE_000006);

			/*
			 * Output the Transaction History Log
			 * (set "key_reflect error" into PAN field for the display in transaction history)
			 */
			internalFormatErrNew.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_ERROR_KEYACTIVATE_BNK);
			internalFormatErrNew.setValue(
					InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, Constants.TRANS_CODE_000006);
			internalFormatErrNew.setValue(InternalFieldKey.HOST_HEADER.SOURCE_ID, Constants.OUTRESCNV_NW_CODE_BANKNET);
			fepMsg.setMessageContent(internalFormatErrNew);

			/*
			 * Edit the queue header
			 * Processing type identifier = "05" Data format identifier = "01"
			 */
			fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
			fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

			/*
			 * Call sendMessage(String queueName, FepMessage message) to send
			 * the message to the queue of Monitor STORE process.
			 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),refer
			 * to "SS0111 System Code Definition(FEP).xls"
			 */
			sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending error message to <" 
					+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
					"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
					"| Data format:" + fepMsg.getDataFormatIdentifier() +
					"| MTI:" + internalFormatErrNew.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
					"| FepSeq:" + fepMsg.getFEPSeq() +
					"| NetworkID: BNK" + 
					"| Transaction Code(service):" + internalFormatErrNew.getValue(
						HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
					"| SourceID: " + internalFormatErrNew.getValue(HOST_HEADER.SOURCE_ID).getValue() +
					"| Destination ID: " + internalFormatErrNew.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
					"| FEP Response Code: "+ internalFormatErrNew.getValue(
							CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
			// End the processmcpProcess.error(th.getMessage(), th);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] execute(): End Process");
			return;
		}

		/*
		 * Output the Transaction History Log
		 * (set "key_reflect" into PAN field for the display in transaction history)
		 */
		internalFormatOk.setValue(InternalFieldKey.HOST_COMMON_DATA.PAN, MSG_SUCCESS_KEYACTIVATE_BNK);
		fepMsg.setMessageContent(internalFormatOk);

		/*
		 * Edit the queue header
		 * Processing type identifier = "05" Data format identifier = "01"
		 */
		fepMsg.setProcessingTypeIdentifier(SysCode.QH_PTI_SAF_REQ);
		fepMsg.setDataFormatIdentifier(SysCode.QH_DFI_FEP_MESSAGE);

		/*
		 * Call sendMessage(String queueName, FepMessage message) to send
		 * the message to the queue of Monitor STORE process.
		 * *queueName=mcpProcess.getQueueName(SysCode.LN_MON_STORE),
		 * refer to "SS0111 System Code Definition(FEP).xls"
		 */
		sendMessage(mcpProcess.getQueueName(SysCode.LN_MON_STORE), fepMsg);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_INFORMATION,"sending a message to <" 
				+ mcpProcess.getQueueName(SysCode.LN_MON_STORE) + ">" +
				"[Process type:" + fepMsg.getProcessingTypeIdentifier() + 
				"| Data format:" + fepMsg.getDataFormatIdentifier() +
				"| MTI:" + internalFormatOk.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue() +
				"| FepSeq:" + fepMsg.getFEPSeq() +
				"| NetworkID: BNK" + 
				"| Transaction Code(service):" + internalFormatOk.getValue(
					HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue() + 
				"| SourceID: " + internalFormatOk.getValue(HOST_HEADER.SOURCE_ID).getValue() +
				"| Destination ID: " + internalFormatOk.getValue(HOST_HEADER.DESTINATION_ID).getValue() +
				"| FEP Response Code: "+ internalFormatOk.getValue(
						CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue() +"]");
	} // End of key_reflection_notification_is() method

	/**
	 * Edit the 0800 request message.
	 *      - sign-on/off
	 *      - echo-test
	 *      - advice
	 * @param fepMsg The Fep Message to edit
	 * @return ISOMsg The ISOMsg have been edited
	 * @throws ISOException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 */
	private ISOMsg edit_0800_message(FepMessage fepMsg)
	throws ISOException, AeonDBException, IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] edit_0800_message() start");
		
		InternalFormat internalMsg;
		Calendar toGmtCal;
		String gmtTimeFormatted;
		String systemTraceAuditNumber;
		String valueForField11;
		String pti = fepMsg.getProcessingTypeIdentifier();;
		int rawOffset;
		long gmtTime;
		int[] unsetVariable0800 = {20, 39, 44, 53, 63, 91, 101, 122, 127};
		String pan;
		
		// retrieve internal format message
		internalMsg = fepMsg.getMessageContent();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0800_message : transaction code: " + internalMsg.getValue(
						InternalFieldKey.HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue());

		/*
		 * [Lutherford Sosuan: 11/24/2010] [Redmine #1011, 1012, 1013, 1014] [error occurred in Brand Process]
		 * set packager
		 */
		isoMsgInstanceVar.setPackager(new GenericValidatingPackager(
				(String)mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE)));

		// set "0800"
		isoMsgInstanceVar.setMTI(SysCode.MTI_0800);

		/*
		 * (2) Primary Account Number(PAN) - set MasterCard Customer ID Number
		 * 
		 * [Megan Quines: 09/10/2010] [Redmine #1047] [FEXC00101 BANKNET Message Control]
		 * [lsosuan: 01/24/2010] [Redmine #2168] [Banknet send prxfix Sign on command error]
		 * If Processing Discrimination of Header is "31"(sign-on( Prefix )) or "32"(sign-off( Prefix ))
		 * 	set PAN of mapHost_Com_Data section of Internal Format(if Space, set BitOFF)
		 */
		if(SysCode.QH_PTI_PREFIX_SIGN_ON.equals(pti) || SysCode.QH_PTI_PREFIX_SIGN_OFF.equals(pti)) {
			pan = internalMsg.getValue(InternalFieldKey.HOST_COMMON_DATA.PAN).getValue();
			if(null != pan) {
				if(Constants.NO_SPACE == pan.trim()) 
					isoMsgInstanceVar.unset(2);
				
				else
					isoMsgInstanceVar.set(2, pan);
			}
		
		/*
		 * If Processing Discrimination of Header is "33"(sign-on) or "34"(sign-off) or  "36"(Echo test)
		 * set MASTERCARD_CUSTOMER_ID_NUMBER from BANKNET Connection Configuration File.
		 */
		} else if(SysCode.QH_PTI_SIGN_ON.equals(pti) || 
				SysCode.QH_PTI_SIGN_OFF.equals(pti) || 
				SysCode.QH_PTI_ECHO_TEST.equals(pti)) {
			
			isoMsgInstanceVar.set(2, (
					(String)mcpProcess.getAppParameter(Keys.XMLTags.MASTERCARD_CUSTOMER_ID_NUMBER)).trim());
		}
		
		/*
		 * (7) Transmission Date and Time - Set system Date and Time(When request message is edited.)
		 */
		toGmtCal = Calendar.getInstance();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0800_message : current time (local): " + 
				sdf_MMDDhhmmss.format(toGmtCal.getTime()));
		
		rawOffset = toGmtCal.getTimeZone().getRawOffset();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0800_message :  rawOffset: " + rawOffset);
		
		gmtTime = toGmtCal.getTimeInMillis() - rawOffset;
		toGmtCal.setTimeInMillis(gmtTime);
		gmtTimeFormatted = sdf_MMDDhhmmss.format(toGmtCal.getTime());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0800_message : current time (gmt): " + gmtTimeFormatted);
		
		isoMsgInstanceVar.set(7, gmtTimeFormatted);

		// (11) Systems Trace Audit Number(STAN) - set the numbered value in FEP
		systemTraceAuditNumber = mcpProcess.getSequence(SysCode.STAN_SYS_TRACE_AUDIT_NUMBER).trim();
		valueForField11 = ISOUtil.padleft(systemTraceAuditNumber, 6, Constants.cPAD_0);
		isoMsgInstanceVar.set(11, valueForField11);

		// (20) Primary Account Number(PAN) Country Code - BitOFF*1
		isoMsgInstanceVar.unset(unsetVariable0800);

		/*
		 * (33) Forwarding Institution ID Code - 
		 * Set ACQUIRING_INSTITUTION_ID_CODE from BANKNET Connection Configuration File.
		 */
		isoMsgInstanceVar.set(33, mcpProcess.getAppParameter(Keys.XMLTags.FW_INSTITUTION_ID_CODE).toString());

		// (39) Response Code - BitOFF
		isoMsgInstanceVar.unset(unsetVariable0800);

		/*
		 * (70) Network Management Information Code
		 * If TRANSACTION CODE is "000007"(SAF Request)
		 */
		// [eotayde 06052014: RM #5485 - Change F70 from 001 to 061 for
		// Prefix Sign-On]
		//[rrabaya 20150317] Revert 5485 manually
		 if(SysCode.QH_PTI_PREFIX_SIGN_ON.equals(pti))
		 isoMsgInstanceVar.set(70,Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_001);
//		if (SysCode.QH_PTI_PREFIX_SIGN_ON.equals(pti))
//			isoMsgInstanceVar.set(70,
//					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061);

		// [ACSS)EOtayde 06162014: RM 5530] Change F70 from 002 to 062 for Prefix Sign-Off
		 //[rrabaya 20150317] Revert RM5530 manually
		 else if (SysCode.QH_PTI_PREFIX_SIGN_OFF.equals(pti))
			isoMsgInstanceVar.set(70,
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_002);
//		else if (SysCode.QH_PTI_PREFIX_SIGN_OFF.equals(pti))
//			isoMsgInstanceVar.set(70,
//					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062);

		else if (SysCode.QH_PTI_SIGN_ON.equals(pti))
			isoMsgInstanceVar.set(70,
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061);

		else if (SysCode.QH_PTI_SIGN_OFF.equals(pti))
			isoMsgInstanceVar.set(70,
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062);
		
		else if(SysCode.QH_PTI_ECHO_TEST.equals(pti))
			isoMsgInstanceVar.set(70,
					Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_270);

		isoMsgInstanceVar.unset(unsetVariable0800);
		
		//[AGerangco:7/6/2012] RM#3916 Unnecessary field validation **start
		if(!SysCode.QH_PTI_ECHO_TEST.equals(pti)) { //do not set if echo test.
		/*
		 * (94) Service Indicator - If sign-on/off, SAF Request,
		 * set based on BANKNET Connection Configuration File
		 */
			isoMsgInstanceVar.set(94, mcpProcess.getAppParameter(Keys.XMLTags.SERVICE_INDICATOR).toString());
	
			/*
			 * (96) Message Security Code
			 * If the MTI is "0800", from set "11110000"
			 * to Set MESSAGE SECURITY CODE from BANKNET Connection Configuration File
			 * 
			 * [Lutherford Sosuan : 11/30/2010] [Redmine #1037] [Corrected SS document together with the code]
			 * [mqueja: 11/22/2011] [update If the MTI is "0800", 
			 *    set MESSAGE SECURITY CODE from BANKNET Connection Configuration File]
			 */
			isoMsgInstanceVar.set(96, mcpProcess.getAppParameter(Keys.XMLTags.MESSAGE_SECURITY_CODE).toString());
		}
		//[AGerangco:7/6/2012] RM#3916 Unnecessary field validation **end
		
		// (101) File Name - set BitOFF
		isoMsgInstanceVar.unset(unsetVariable0800);

		// return edited request message
		return isoMsgInstanceVar;
	}// End of edit_0800_message()

	/**
	 * Edit the 0810 response message.
	 *      - sign-on/off
	 *      - echo-test
	 * @param fepMsg The Fep Message to edit
	 * @return ISOMsg The ISOMsg have been edited
	 * @throws ISOException 
	 */
	private ISOMsg edit_0810_message(FepMessage fepMsg) throws ISOException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] edit_0810_message() start");

		ISOMsg isoMsg = new ISOMsg();
		int[] unsetVariable_0810 = {44, 60, 62, 100, 120, 127};

		// Edit the 0810 response message
		isoMsg = fepMsg.getMessageContent();

		// set packager
		isoMsg.setPackager(new GenericValidatingPackager(
				(String)mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE)));

		// set "0810"
		isoMsg.setMTI(SysCode.MTI_0810);

		// (39) Response Code - set '00'
		isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_SUCCESS);

		isoMsg.unset(unsetVariable_0810);

		/*
		 * (48) Additional Data - Private - 
		 * If the MTI is "0800" and DE70 is "161", Echo, For other cases, set BitOFF
		 */
		if (!Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_161.equals(isoMsg.getValue(70))) {
			// set BitOFF
			isoMsg.unset(48);
		}

		// return edited request message
		return isoMsg;
	}// End of edit_0810_message()

	/**
	 * Edit the 0630 response message.
	 * @param fepMsg The Fep Message to edit
	 * @return ISOMsg The ISOMsg have been edited
	 * @throws ISOException 
	 */
	private ISOMsg edit_0630_message(FepMessage fepMsg) throws ISOException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] edit_0630_message() start");

		ISOMsg isoMsg = new ISOMsg();
		int[] unsetVariable_0630 = {2, 44, 48, 60, 62, 70, 120, 127};

		// get iso msg from fepMsg
		isoMsg = fepMsg.getMessageContent();

		// set packager
		isoMsg.setPackager(new GenericValidatingPackager(
				(String)mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE)));

		// set "0630"
		isoMsg.setMTI(Constants.MTI_0630);

		// (2) Primary Account Number(PAN)/Member Group ID - set BitOFF
		isoMsg.unset(unsetVariable_0630);

		// (39) Response Code - set '00'
		isoMsg.set(39, Constants.FLD39_RESPONSE_CODE_SUCCESS);

		/*
		 * (62) Intermediate Network Facility(INF) Data - If the MTI is  "0620",
		 *  Echo. (However, if the Request Message
		 * is BitOFF, setBitOFF) if field 62 equals to null
		 */
		if (isoMsg.getValue(62) == null) {

			// set BitOFF
			isoMsg.unset(unsetVariable_0630);
		}

		isoMsg.unset(unsetVariable_0630);

		// return edited request message
		return isoMsg;
	}// End of edit_0630_message()

	/**
	 * Edit the 0302 request message.
	 * @param fepMsg The Fep Message to edit
	 * @return ISOMsg The ISOMsg have been edited
	 * @throws ISOException 
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 */
	private ISOMsg edit_0302_message(FepMessage fepMsg) throws ISOException,
	AeonDBException, IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetCommand] edit_0302_message() start");

		ISOMsg isoMsg;
		InternalFormat internalMsg;
		String valueForF2;
		Calendar toGmtCal;
		String gmtTimeFormatted;
		String systemTraceAuditNumber;
		String valueForField11;
		String transCodeIssuerFileUpdate;
		int rawOffset;
		long gmtTime;
		int[] unsetVariable_0302 = {122, 127};
		
		// retrieve internal format message
		internalMsg = fepMsg.getMessageContent();

		isoMsg = new ISOMsg();

		/*
		 * convert InternalFormat msg to ISOMsg
		 * Redmine #2335: Using empty fields in the internal format is causing exception
		 * Solution: Do not use internalFormatProcess anymore, since we can set the values in this method
		 * isoMsg = internalFormatProcess.createISOMsgFromInternalFormat(internalMsg); 
		 * 
		 *  set packager
		 */
		isoMsg.setPackager(new GenericValidatingPackager(
				(String)mcpProcess.getAppParameter(PACKAGE_CONFIGURATION_FILE)));

		// set "0302"
		isoMsg.setMTI(SysCode.MTI_0302);

		/*
		 * (2) Primary Account Number(PAN) - set PAN of the Common data
		 * [Feona May Samson: 08/30/2010] [Redmine #978] [FEXC00101 BANKNET Message Control]
		 * [Feona May Samson: 08/30/2010] [Redmine #978]
		 * [set field by PAN of mapHost_NW_Uniq_Data section of Internal 
		 * Format(if Space, set BitOFF]
		 */
		if(null != internalMsg || internalMsg.toString().trim().length() > 0){
			valueForF2 = internalMsg.getValue(
					IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.PRIMARY_ACCOUNT_NUMBER).getValue();
			
			if (valueForF2 != null && !valueForF2.trim().equals(NO_SPACE))
				isoMsg.set(2, valueForF2);
		}

		// (7) Transmission Date and Time - Set system Date and Time(When  request message is edited.)
		toGmtCal = Calendar.getInstance();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetCommand] edit_0302_message : current time (local): " + 
				sdf_MMDDhhmmss.format(toGmtCal.getTime()));
		
		rawOffset = toGmtCal.getTimeZone().getRawOffset();
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0302_message : rawOffset : " + rawOffset);
		
		gmtTime = toGmtCal.getTimeInMillis() - rawOffset;
		toGmtCal.setTimeInMillis(gmtTime);
		gmtTimeFormatted = sdf_MMDDhhmmss.format(toGmtCal.getTime());
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
				"[BanknetCommand] edit_0302_message : current time (gmt): " + gmtTimeFormatted);
		
		isoMsg.set(7, gmtTimeFormatted);

		/*
		 * (11) Systems Trace Audit Number(STAN) - set the numbered value in FEP
		 * [Feona May Samson: 08/30/2010] [Redmine #979] [FEXC00101 BANKNET Message Control]
		 */
		systemTraceAuditNumber = mcpProcess.getSequence(SysCode.STAN_SYS_TRACE_AUDIT_NUMBER).trim();
		valueForField11 = ISOUtil.padleft(systemTraceAuditNumber, 6, Constants.cPAD_0);
		isoMsg.set(11, valueForField11);

		/*
		 * (33) Forwarding Institution ID Code - Set ACQUIRING_INSTITUTION_ID_CODE from BANKNET Connection
		 * Configuration File.
		 * [Megan Quines: 09/13/2010] [Redmine #1047] [FEXC00101 BANKNET Message Control]
		 */
		isoMsg.set(33, mcpProcess.getAppParameter(Keys.XMLTags.FW_INSTITUTION_ID_CODE).toString());

		// (91) File Update Code - set BitOFF if Transaction Code of Issuer File Update is "051301"
		if(null != internalMsg || internalMsg.toString().trim().length() > 0){
			transCodeIssuerFileUpdate = internalMsg.getValue(IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey
					.TRANSACTION_CODE).getValue();
			
			if (Constants.TRANS_CODE_051301.equals(transCodeIssuerFileUpdate))

				// set "1"
				isoMsg.set(91, Constants.FLD91_FILE_UPDATE_CODE_1);

			else if (Constants.TRANS_CODE_051302.equals(transCodeIssuerFileUpdate))
				/*
				 * if Transaction Code of Issuer File Update is "051302"
				 * set "2"
				 * [MLiwanag: 09/13/2010] [Redmine #1050] [FEXC00101 BANKNET Message Control]
				 */
				isoMsg.set(91, Constants.FLD91_FILE_UPDATE_CODE_2); 

			else if (Constants.TRANS_CODE_051303.equals(transCodeIssuerFileUpdate))
				/*
				 * if Transaction Code of Issuer File Update is "051303"
				 * set "3"
				 */
				isoMsg.set(91, Constants.FLD91_FILE_UPDATE_CODE_3);

			else if (Constants.TRANS_CODE_051304.equals(transCodeIssuerFileUpdate))
				/*
				 * if Transaction Code of Issuer File Update is "051304"
				 * set "5"
				 */
				isoMsg.set(91, Constants.FLD91_FILE_UPDATE_CODE_5);
		}

		/*
		 * (96) Message Security Code - set Message Security Code of Issuer File Update
		 * [mqueja:12/20/2011] [Redmine#3467] [value set to DE96 changed from internalFormat to Config value]
		 */
		if(null != internalMsg || internalMsg.toString().trim().length() > 0)
			isoMsg.set(96, mcpProcess.getAppParameter(Keys.XMLTags.MESSAGE_SECURITY_CODE).toString());

		// (101) File Name - set File Name of Issuer File Update
		if(null != internalMsg || internalMsg.toString().trim().length() > 0)
			isoMsg.set(101, internalMsg.getValue(
					IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.FILE_NAME).getValue());
		
		/*
		 * [Feona May Samson: 08/30/2010] [Redmine #980] [BANKNET MCP edited the Request message]
		 * (120) Record Data - set Record Data of Issuer File Update(However, in case of none:BitOFF)
		 */
		if(null != internalMsg || internalMsg.toString().trim().length() > 0){
			String valueForF120 = internalMsg.getValue(
					IssuerFileUpdateFieldKey.BANKNET_ISSFileUpdKey.RECORD_DATA).getValue();
			
			if (valueForF120 != null && !valueForF120.trim().equals("")) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetCommand] edit_0302_message : Total Length(120):" + valueForF120.length());

				if (isoMsg.getString(91).equals(Constants.FLD91_FILE_UPDATE_CODE_3) || 
						isoMsg.getString(91).equals(Constants.FLD91_FILE_UPDATE_CODE_5)) {
					
					String newValueForF120 = valueForF120.substring(0, 19);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetCommand] edit_0302_message : Field 120: " + newValueForF120);
					
					isoMsg.set(120, newValueForF120);
				
				} else
					isoMsg.set(120, valueForF120);
			}// End of valueForF120 = null
			
		}// End of internalMsg = null

		isoMsg.unset(unsetVariable_0302);

		// return edited request message
		return isoMsg;
	}// End of edit_0302_message()
}// End of class
