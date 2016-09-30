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
 * June-24-10 ccalanog			V0.0.1		: added logic for creating messages
 * May-13-10  ccalanog       	initial
 * 			
 */

package com.aeoncredit.fep.core.adapter.brand.banknet.mcp;

import static com.aeoncredit.fep.core.adapter.brand.common.Constants.*;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOField;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

import com.aeoncredit.fep.common.SysCode;
import com.aeoncredit.fep.core.adapter.brand.common.Constants;
import com.aeoncredit.fep.core.adapter.brand.common.FepUtilities;
import com.aeoncredit.fep.core.adapter.brand.common.IInternalFormatProcess;
import com.aeoncredit.fep.core.adapter.brand.common.Keys;
import com.aeoncredit.fep.core.adapter.brand.common.MasterCardMoneySendParser;
import com.aeoncredit.fep.core.adapter.local.edc.common.Constant;
import com.aeoncredit.fep.core.internalmessage.InternalFormat;
import com.aeoncredit.fep.core.internalmessage.exception.TypeNotMatchedException;
import com.aeoncredit.fep.core.internalmessage.keys.ATMCashAdvanceFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.BKNMDSHKFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.IInternalKey;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey;
import com.aeoncredit.fep.core.internalmessage.keys.ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE;
import com.aeoncredit.fep.core.internalmessage.keys.EDCShoppingFieldKey.EDC_SHOPPING;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.ACE_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.CONTROL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_COMMON_DATA;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_FEP_ADDITIONAL_INFO;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.HOST_HEADER;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.IC_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.OPTIONAL_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.InternalFieldKey.THREED_D_SECURE_INFORMATION;
import com.aeoncredit.fep.core.internalmessage.keys.PGWFieldKey.PGW;
import com.aeoncredit.fep.framework.exception.AeonDBException;
import com.aeoncredit.fep.framework.exception.IllegalParamException;
import com.aeoncredit.fep.framework.log.LogOutputUtility;
import com.aeoncredit.fep.framework.mcp.IMCPProcess;
import com.aeoncredit.fep.framework.socketcommu.MasterCardMsgParser;

public class BanknetInternalFormatProcess implements IInternalFormatProcess {

	private IMCPProcess mcpProcess;

	/** Variables used for Exception Handling */
	private String[] args = new String[2];
	private String sysMsgID = NO_SPACE;
	
	/** Used for ASI and MoneySend transactions */
	//[rrabaya:11/28/2014]RM#5850 Add fields for Account Status Inquiry : Start
	private boolean isPurchaseASI;
	private boolean isPaymentASI;
	private boolean isMoneySendPaymentASI;
	private boolean isASI;
	//[rrabaya:11/28/2014]RM#5850 Add fields for Account Status Inquiry : End
	private boolean isMoneySendPaymentAuth; // [eotayde 12182014: RM 5838]
	
	/**
	 * Constuctor method
	 * @param mcpProcess The instance of AeonProcessImpl
	 */
	public BanknetInternalFormatProcess(IMCPProcess mcpProcess) {
		this.mcpProcess = mcpProcess;		
	}

	/**
	 * Initializes the internal format
	 * @return created InternalFormat
	 */
	public InternalFormat initInternalFormat() {
		return new InternalFormat();
	}

	/**
	 * Create the ISOMsg according to the InternalFormat. 
	 * Refer to SS0106 Message Interface Item Editing Definition(FEXC001 FINF001 Internal Format)
	 * @param originalMsg The recieved ISOMSg
	 * @return InternalFormat The created Internal Format
	 */
	public InternalFormat createInternalFormatFromISOMsg(ISOMsg originalMsg) {
				
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetInternalFormatProcess] : createInternalFormatFromISOMsg");
		
		String transCode;
		InternalFormat internalMsg = initInternalFormat();
		try {
			String mti = originalMsg.getMTI();
			String pan = getPAN(originalMsg);

			String field3Sub1 = "";
			String field7Sub1yyyymmdd = "";
			String field7Sub2time = "";
			String field22 = "";

			String field48Sub42 = "";
			String field48Sub43 = "";      //substring 0,1
			String field48Sub43_2_20 = ""; //substring 2,20
			String field48Sub43_1_28 = ""; //substring 1,28
			String field48Sub44 = null;    //encoded by base 64
			String field48Sub92 = null;			
			
			String field70 = originalMsg.getString(70);
			String field61Sub10 = "";
			String field90Sub2 = ""; 
			ISOMsg inner;
			ISOMsg field61;
			// Start Redmine #4340 - Manual Cash transaction should be treated as purchase [MAguila: 20130528]
			String mcc = NO_SPACE;
			// End Redmine #4340 [MAguila: 20130528]

			if(originalMsg.hasField(3)) {
				field3Sub1 = originalMsg.getString(3).substring(0, 2);
			}
			if(originalMsg.hasField(7)) {
				field7Sub1yyyymmdd = ISODate.formatDate(Calendar.getInstance().getTime(),
						Constants.DATE_FORMAT_yyyy) 
				+ originalMsg.getString(7).substring(0, 4);
				field7Sub2time = originalMsg.getString(7).substring(4, 10);
			}
			if(originalMsg.hasField(22)) {
				field22 = originalMsg.getString(22).substring(0, 2);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"DE 22 : " + field22);
			}
			if(originalMsg.hasField(48)){
				inner = (ISOMsg) originalMsg.getValue(48);
				
				try{
					if(inner.hasField(42)) {
						field48Sub42 = inner.getString(42); //position 1-3 (fixed with 'endswith')
					}
				}catch (ClassCastException e) {
					field48Sub42 = Constants.PAD_000;
				}catch (Exception e) {
					field48Sub42 = Constants.PAD_000;
				}

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"DE 48 Sub 42: " + field48Sub42);

				try{
					if(inner.hasField(43)) {
						field48Sub43 = inner.getString(43); //use encoded value for comparison
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
								"DE 48 Sub 43: " + field48Sub43);
						
						/*
						 * decode from Base64 position 1-28 if UCAF (length is 28)
						 * since String indexing starts at 0, we want the substring at 0-27
						 * use substring(0, 28) because this method includes first index but excludes last index	
						 */			 
						field48Sub43_1_28 = FepUtilities.base64ToString(field48Sub43.substring(0, 28));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
								"DE 48 Sub 43.1.28 decoded by base64: " + field48Sub43_1_28);
						
						/*
						 * get data as-is position 2-21 if 3D for Visa (length is 20 - see Banknet Manual)
						 * since String indexing starts at 0, we want the substring at 1-20
						 * use substring(1, 21) because this method includes first index but excludes last index
						 */
						field48Sub43_2_20 = field48Sub43.substring(1, 21);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
								"DE 48 Sub 43.2.20: " + field48Sub43_2_20);
					}
				}catch (ClassCastException e) {
					field48Sub43 = ISOUtil.padleft("", 28, cPAD_0);
				}catch (Exception e) {
					field48Sub43 = ISOUtil.padleft("", 28, cPAD_0);
				}
				
				if(inner.hasField(44)) {
					field48Sub44 = inner.getString(44);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
							"DE 48 Sub 44: " + field48Sub44);
				}
				if(inner.hasField(92)) {
					field48Sub92 = inner.getString(92);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
							"DE 48 Sub 92: " + field48Sub92);
				}
			}
			
			if(originalMsg.hasField(61)) {
				field61 = (ISOMsg) originalMsg.getValue(61);
				//[mquines:12/09/2011] Added hasField() to prevent null pointer exception
				field61Sub10 = field61.hasField(10)?field61.getString(10):"";
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"DE 61 Sub 10: " + field61Sub10);
			}			
			if(originalMsg.hasField(90)) {
				field90Sub2 = originalMsg.getString(90).substring(4, 10);
			}			
			
			//[rrabaya:11/28/2014]RM#5850 check ASI for 0100 and 0120 Transaction : Start
			isASI = isASI(originalMsg);
			if((SysCode.MTI_0100.equals(mti)
					|| SysCode.MTI_0120.equals(mti))
					&& isASI){
				checkASI(originalMsg);
			}
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetInternalFormatProcess] : isPurchaseASI: " + isPurchaseASI);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetInternalFormatProcess] : isPaymentASI: " + isPaymentASI);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetInternalFormatProcess] : isMoneySendASI: " + isMoneySendPaymentASI);
			//[rrabaya:11/28/2014]RM#5850 check ASI for 0100 and 0120 Transaction : End

			// [START] [eotayde 12182014: RM 5838]
			isMoneySendPaymentAuth = isMoneySendPaymentAuth(originalMsg);
			// [END] [eotayde 12182014: RM 5838]
			
			transCode = getTransactionCode(mti,field3Sub1,field70,originalMsg);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"Transaction Code: " + transCode );
			
			/*
			 * Control Information -------------------------------//
			 * 4. IC Flag
			 */
			internalMsg.setValue(CONTROL_INFORMATION.IC_FLAG,
					((field22.startsWith(Constants.FLD22_1_PAN_ENTRY_MODE_05)) 
					| (field22.startsWith(Constants.FLD22_1_PAN_ENTRY_MODE_81) 
					| (field22.startsWith(Constants.FLD22_1_PAN_ENTRY_MODE_07))) 
					&& originalMsg.hasField(55) && originalMsg.hasField(35)) 
						? SysCode.IC_INFO_EXIST 
						: SysCode.IC_INFO_NOT_EXIST);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : IC_FLAG: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.IC_FLAG).getValue());
			
			// 5. With PIN or Not
			internalMsg.setValue(CONTROL_INFORMATION.WITH_PIN_OR_NOT, 
					(originalMsg.hasField(52) 
							? SysCode.WITH_ZPK_PIN
							: SysCode.WITHOUT_PIN));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : WITH_PIN_OR_NOT: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.WITH_PIN_OR_NOT).getValue());
			
			// 6 FEP Response Code
			internalMsg.setValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE, Constants.SPACE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : FEP_RESPONSE_CODE: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.FEP_RESPONSE_CODE).getValue());			
			
			/*
			 * 8. 3D Secure Flag
			 * 
			 * [mquines: 20110812] Redmine 3258: Updated logic based on new SS
			 */	
			if((field22.startsWith(Constants.FLD22_1_PAN_ENTRY_MODE_81) || field22.startsWith(Constants.FLD22_1_PAN_ENTRY_MODE_01)) 
					&& (field61Sub10.startsWith(Constants.FLD61_10_CARDHOLDER_ACTIVATED_TERMINAL_LEVEL_6) 
							|| field61Sub10.startsWith(Constants.FLD61_10_NOT_A_CAT_TRANSACION_0))) {
				if(field48Sub42.endsWith(UCAF_SUPPORTED_AND_PROVIDED_2) || (field48Sub42.endsWith(UCAF_NOT_SUPPORTED_0) && !"".equals(field48Sub43))) {
					internalMsg.setValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG, SysCode.THREED_SECURE_WITHIN);
				} else {
					internalMsg.setValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG, SysCode.THREED_SECURE_WITHOUT);
				}
			} else {
				internalMsg.setValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG, SysCode.THREED_SECURE_WITHOUT);
			}
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : THREE_D_SECURE_FLAG: "
					+ internalMsg.getValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG).getValue());
				
			// 9. Switching Action Flag
			internalMsg.setValue(CONTROL_INFORMATION.SWITCHING_ACTION_FLAG, 
					(SysCode.MTI_0620.equals(mti)) 
						    ? SysCode.DIRECT_HOST 
					: (SysCode.MTI_0120.equals(mti) || SysCode.MTI_0420.equals(mti)) 
							? SysCode.ACCRUAL_PROCESS 
							: SysCode.NORMAL_PROCESS);

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SWITCHING_ACTION_FLAG: "
					+ internalMsg.getValue(CONTROL_INFORMATION.SWITCHING_ACTION_FLAG).getValue());
			
			/*
			 * 10. Reversal Posible Flag
			 * [mqueja:08/01/2011] [Redmine #2914] 
			 * [added 'not equal' condition to ON reversal flag for all reversal transactions]
			 */
			internalMsg.setValue(CONTROL_INFORMATION.REVERSAL_POSSIBLE_FLAG, 
					(( SysCode.MTI_0100.equals(mti) 
					   || SysCode.MTI_0120.equals(mti)
					   || SysCode.MTI_0400.equals(mti)
					   || SysCode.MTI_0420.equals(mti))
					   && (!"".equals(transCode)) &&
					   !Constants.SUFFIX_TRANS_CODE_INQUIRY_CREDIT_LIMIT.equals(transCode.substring(2, 6))) 
					? SysCode.REVERSAL_POSSIBLE 
					: SysCode.REVERSAL_IMPOSSIBLE);
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : REVERSAL_POSSIBLE_FLAG: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.REVERSAL_POSSIBLE_FLAG).getValue());

			/*
			 * If TRANSACTION CODE is "01XXXX", set "01"
			 * If TRANSACTION CODE is "02XXXX", set "02"
			 * If TRANSACTION CODE is "03XXXX", set "03"
			 * If TRANSACTION CODE is "04XXXX", set "04"
			 * For other TRANSACTION CODE, set first two characters of the TRANSACTION CODE.
			 * 
			 * 13. Transaction type
			 */
			if((null != transCode) && (!(NO_SPACE).equals(transCode))){
				internalMsg.setValue(CONTROL_INFORMATION.TRANSACTION_TYPE, transCode.substring(0,2));	
			}

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_TYPE: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.TRANSACTION_TYPE).getValue());
			
			/*
			 * Optional Information -------------------------------//
			 * 
			 * 18. Message Type
			 */
			internalMsg.setValue(OPTIONAL_INFORMATION.MESSAGE_TYPE, mti);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MESSAGE_TYPE: " 
					+ internalMsg.getValue(OPTIONAL_INFORMATION.MESSAGE_TYPE).getValue());
			
			// 22. CVV2 value
			if(null != field48Sub92) {
				internalMsg.setValue(OPTIONAL_INFORMATION.CVV2_VALUE, field48Sub92);

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CVV2_VALUE: " 
						+ internalMsg.getValue(OPTIONAL_INFORMATION.CVV2_VALUE).getValue());
			}		
			
			/*
			 * 25. POS Entry Mode
			 * [salvaro:20111025] removed setting of MDS POS Entry mode
			 */
			if(originalMsg.hasField(22)) {
				internalMsg.setValue(OPTIONAL_INFORMATION.POS_ENTRY_MODE, originalMsg.getString(22));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : POS_ENTRY_MODE: " 
						+ internalMsg.getValue(OPTIONAL_INFORMATION.POS_ENTRY_MODE).getValue());
			}
			
			/*
			 * Host header -------------------------------//
			 * 
			 * 27. Header Indicator
			 */
			internalMsg.setValue(HOST_HEADER.HEADER_INDICATOR, Constants.DATA_INDICATOR_HEADER);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : HEADER_INDICATOR: " 
					+ internalMsg.getValue(HOST_HEADER.HEADER_INDICATOR).getValue());
			
			// 29. Port Indicator
			internalMsg.setValue(HOST_HEADER.PORT_INDICATOR, Constants.DATA_INDICATOR_PORT);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : PORT_INDICATOR: " 
					+ internalMsg.getValue(HOST_HEADER.PORT_INDICATOR).getValue());
			
			// 30. Host Request response indicator
			internalMsg.setValue(HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR, SysCode.HOST_REQRES_INDICATOR_REQUEST);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : REQUEST_OR_RESPONSE_INDICATOR: " 
					+ internalMsg.getValue(HOST_HEADER.REQUEST_OR_RESPONSE_INDICATOR).getValue());

			// 31. Source ID
			internalMsg.setValue(HOST_HEADER.SOURCE_ID, SysCode.BKN);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SOURCE_ID: " 
					+ internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue());
			
			// 32. Destination Id
			internalMsg.setValue(HOST_HEADER.DESTINATION_ID, SysCode.HST);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DESTINATION_ID: " 
					+ internalMsg.getValue(HOST_HEADER.DESTINATION_ID).getValue());
			
			// 33. MESSAGE PROCESSING DATE/TIME
			/*[MQuines:20111207] Modified the implementation in setting of MESSAGE_PROCESSING_DATE_TIME
			 * From field7Sub1yyyymmdd + field7Sub2time to mcpProcess.getSystime().toString().substring(0, 14)
			 */
			internalMsg.setValue(HOST_HEADER.MESSAGE_PROCESSING_DATE_TIME, mcpProcess.getSystime().toString().substring(0, 14));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MESSAGE_PROCESSING_DATE_TIME: " 
					+ internalMsg.getValue(HOST_HEADER.MESSAGE_PROCESSING_DATE_TIME).getValue());
			
			// 38. FEP Event Code
			internalMsg.setValue(HOST_HEADER.FEP_EVENT_CODE, Constants.FEP_EVENT_CODE_1000);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : FEP_EVENT_CODE: " 
					+ internalMsg.getValue(HOST_HEADER.FEP_EVENT_CODE).getValue());
			
			// 39. HOST Result Code
			internalMsg.setValue(HOST_HEADER.HOST_RESULT_CODE, Constants.HOST_RESULT_CODE_0000);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : HOST_RESULT_CODE: " 
					+ internalMsg.getValue(HOST_HEADER.HOST_RESULT_CODE).getValue());
			
			/*
			 *  [MAguila: 06/17/2011 Related to Redmine # 2575]
			 *  [FEP Stand In Result Internal Field Key ]
			 *  
			 *  38 Authorization Judgment Division
			 */
			if (mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
				if (SysCode.MTI_0120.equals(mti) || SysCode.MTI_0420.equals(mti)) {
					internalMsg.setValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION, Constants.AUTHORIZATION_JUDGMENT_DIVISION_3);
				}
			} else {
				internalMsg.setValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION, Constants.SPACE);
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"AUTHORIZATION JUDGMENT DIVISION " + internalMsg.getValue(HOST_HEADER.AUTHORIZATION_JUDGMENT_DIVISION).getValue());
			
			/*
			 * Host Fep Add Info -------------------------------//
			 * 
			 * 41. Data Indicator
			 */
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.DATA_INDICATOR, Constants.DATA_INDICATOR_FEP);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DATA_INDICATOR: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.DATA_INDICATOR).getValue());
			
			// 42. Fep Length Data
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_DATA_LENGTH, Constants.DATA_LENGTH_FEP);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : FEP_DATA_LENGTH: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.FEP_DATA_LENGTH).getValue());
			
			// 43. Transaction Code
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE, transCode); // from method
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_CODE: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue());
			
			// 44. Reversal Advice Flag
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.REVERSAL_ADVICE_FLAG, 
					(transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE)) 
							? SysCode.REVERSAL_POSSIBLE 
							: SysCode.REVERSAL_IMPOSSIBLE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : REVERSAL_ADVICE_FLAG: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.REVERSAL_ADVICE_FLAG).getValue());
			
			/*
			 * 46 FEP Stand-in Result
			 * [salvaro:11/28/2011] [Redmine#3440] Updated Setting of Fep Standin Result
			 * if has field 38:
			 * 	if field 39 equal to approved list of response code (08,10,87,etc) || field39 not exist
			 * 		set FEP_STANDIN_RESULT = '00000'
			 * 	else 
			 * 		set FEP_STANDIN_RESULT = '120' +  field39 //adjust for amex
			 * else if no field 38:
			 *   set FEP_STANDIN_RESULT = '12999'
			 *  
			 * [mqueja:11/23/2011] [added checking of DE38 if DE39 is not present]
			 * [mqueja:11/23/2011] [Redmine#3440] [added response code '17' & '68']
			 * [mqueja:11/25/2011] [Redmine#3440] [changed response code to '00', '08', '10', '85' & '87']
			 */
//		 44. Reversal Advice Flag
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.REVERSAL_ADVICE_FLAG, 
					(transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE)) 
							? SysCode.REVERSAL_POSSIBLE 
							: SysCode.REVERSAL_IMPOSSIBLE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
					"[BanknetInternalFormatProcess] : REVERSAL_ADVICE_FLAG: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.REVERSAL_ADVICE_FLAG).getValue());
			
			
			/*Add Start - [etayum] 10/23/2014 RM#5891 Card Member Indicator is NULL in 0420 message
			* set Aeon Flag to 1 for Reversal Advice
			* Aeon Flag is equal to Reversal Advice Flag
			*/
				
			internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.AEON_CARD, 
					com.aeoncredit.fep.core.fepprocess.common.Constant.AEONCARD);
				
			//Add End - [etayum] 10/23/2014
			
			
			/* 46 FEP Stand-in Result
			 * [salvaro:11/28/2011] [Redmine#3440] Updated Setting of Fep Standin Result
			 * if has field 38:
			 * 	if field 39 equal to approved list of response code (08,10,87,etc) || field39 not exist
			 * 		set FEP_STANDIN_RESULT = '00000'
			 * 	else 
			 * 		set FEP_STANDIN_RESULT = '120' +  field39 //adjust for amex
			 * else if no field 38:
			 *   set FEP_STANDIN_RESULT = '12999'
			 *  
			 * [mqueja:11/23/2011] [added checking of DE38 if DE39 is not present]
			 * [mqueja:11/23/2011] [Redmine#3440] [added response code '17' & '68']
			 * [mqueja:11/25/2011] [Redmine#3440] [changed response code to '00', '08', '10', '85' & '87']
			 * [ccalanog:11/15/2011] [Redmine#3418] [changed values set for FEP_STANDIN_RESULT if DE39 is not Approved]
			 * [ccalanog:03/06/2012] [Redmine#3801] [updated logic to set FEP_STANDIN_RESULT as 00000 for appoved DE39 list]
			 */
			if (mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
				if (SysCode.MTI_0120.equals(mti) || SysCode.MTI_0420.equals(mti)) {
						if (originalMsg.hasField(39)) {
							List<String> POSSIBLE_RESPONSE_CODE_LIST = Arrays.asList(
									FLD39_RESPONSE_CODE_SUCCESS,
									FLD39_RESPONSE_CODE_HONOR_WITH_ID,
									FLD39_RESPONSE_CODE_PARTIAL_APPROVAL,
									FLD39_RESPONSE_CODE_PURCHASE_AMOUNT_ONLY_NO_CASH,
									FLD39_RESPONSE_CODE_FOR_AVS_BALANCE_PIN,
									FLD39_RESPONSE_CODE_FOR_AVS_BALANCE_PIN,
									// Start Redmine 4425: MAguila 20130628 added F39=82 in the list
									FLD39_RESPONSE_CODE_TIMEOUT_AT_ISSUER);
									// End Redmine 4425
							if(POSSIBLE_RESPONSE_CODE_LIST.contains(originalMsg.getString(39))) {
								internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT, 
										STANDIN_RESULT_APPROVE);
							} else {
								internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT,
										PREFIX_FSR_120 + originalMsg.getString(39));
							}
						} else if(originalMsg.hasField(38)) {
							internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT, 
									STANDIN_RESULT_APPROVE);
						} else {
							internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT, 
									STANDIN_RESULT_APPROVE);
						}
				//[rrabaya 07/22/2015] RM#6272 [MasterCard] FEP Standin Result is null for 0100 transaction : Start
				} else {
					internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT, SPACE_00000);
				}
				//[rrabaya 07/22/2015] RM#6272 [MasterCard] FEP Standin Result is null for 0100 transaction : End
			} else {
				internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT, SPACE_00000);
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"FEP STANDIN RESULT " + internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.FEP_STANDIN_RESULT).getValue());
			
			/*
			 * [msayaboc 07-01-11] Redmine Issue #2836 - ACE Values in Internal Format 
			 * 47. ACE Referral Code
			 * 48. ACE Reply Code
			 * 49. ACE Reply Risk Code
			 */
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.ACE_REFERRAL_CODE, PAD_00);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ACE REFERRAL CODE " + PAD_00);
			
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.ACE_REPLY_CODE, SPACE_0000);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ACE REPLY CODE " + SPACE_0000);
			
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.ACE_REPLY_RISK_CODE, SPACE_0000);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ACE REPLY RISK CODE " + SPACE_0000);
			
			/*
			 * Members info. -------------------------------//
			 * 50 Card Member Indicator
			 */
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.CARD_MEMBER_INDICATOR, Constants.SPACE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"CARD MEMBER INDICATOR " + Constants.SPACE);
			
			/* Modify Stripe Manual field
			 * aagustin
			 * 11/26/2014 
			 * For Redmine 5952
			 */
			// 51. Stripe/Manual
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.STRIPE_MANUAL, 
					 (originalMsg.hasField(55)) 
					 	? Constants.STRIPE_MANUAL_CHIP 
					: (originalMsg.hasField(35) || originalMsg.hasField(45)) 
						? Constants.STRIPE_MANUAL_STRIPE 
					: (originalMsg.hasField(2)) 
						? Constants.STRIPE_MANUAL_MANUAL 
						: Constants.SPACE);
			
			// 52. Track Indicator
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.TRACK_INDICATOR, 
					(originalMsg.hasField(35)) 
						? Constants.TRACK_INDICATOR_TRACK2 
					: (originalMsg.hasField(45)) 
						? Constants.STRIPE_MANUAL_STRIPE 
					: (originalMsg.hasField(55)) 
						? Constants.STRIPE_MANUAL_CHIP 
						: Constants.SPACE);
			
			/*
			 * FEP Check Results Info
			 * 54. CVV/CVC Indicator
			 * [mqueja:20110715] [Redmine #3138] [CVVCVC Checking]
			 */
			String cvvCvcIndicator = null;
			
			//Update Start - [etayum] 07/23/2014 RM#5727 logging update from FIELD_48_SUB_49 to FIELD_48_SUB_92
			if(null != field48Sub92){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"FIELD_48_SUB_92: " + field48Sub92);
			//Update End - [etayum] 07/23/2014
				
				if(originalMsg.hasField(35) || originalMsg.hasField(45)){
					
					cvvCvcIndicator = Constants.CVV_CVV2_INFO_EXIST;
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"FIELD_35_OR_45: " + cvvCvcIndicator);
					
				}else {
					cvvCvcIndicator = SysCode.CVV2_CHECK_INFO_EXIST;
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
							"NO FIELD_35_AND_45: " + cvvCvcIndicator);
				} // End of inner if
				
			} else if(originalMsg.hasField(35) || originalMsg.hasField(45)){
				
				cvvCvcIndicator = SysCode.CVV_CHECK_INFO_EXIST;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"NO_FIELD_48_SUB_49");
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"FIELD_35_OR_45: " + cvvCvcIndicator);
				
			} else {
				cvvCvcIndicator = SysCode.CVV_CHECK_INFO_NOT_EXIST;
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"NO_FIELD_48_SUB_49_AND_FIELD_35_AND_45");
			} // End of if
			
			internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_INDICATOR, cvvCvcIndicator);
			
			/*
			 * [msayaboc 12-07-2011] Setting of CVV_CVC_CHECK_RESULT value 
			 */
			if ((SysCode.CVV_CHECK_INFO_NOT_EXIST.equals(internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_INDICATOR).getValue()))
					&& (SysCode.CVV_CHECK_INFO_NOT_EXIST.equals(internalMsg.getValue(CONTROL_INFORMATION.SECOND_ICVV_CHECK).getValue()))) {
				internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_CHECK_RESULT, CVV_CVC_CHECK_RESULT_00);
			} else {
				internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_CHECK_RESULT, SPACE);
			} // End of IF-ELSE statement
				
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CVV_CVC_CHECK_RESULT: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_CHECK_RESULT).getValue());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : STRIPE_MANUAL: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.STRIPE_MANUAL).getValue());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRACK_INDICATOR: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRACK_INDICATOR).getValue());
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CVV_CVC_INDICATOR: " 
					+ internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.CVV_CVC_INDICATOR).getValue());
			
			// Com_data
			// 62. Data Indicator
			internalMsg.setValue(HOST_COMMON_DATA.DATA_INDICATOR, Constants.DATA_INDICATOR_COM);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DATA_INDICATOR: " 
					+ internalMsg.getValue(HOST_COMMON_DATA.DATA_INDICATOR).getValue());
			
			// 63. Common Data Length
			internalMsg.setValue(HOST_COMMON_DATA.COMMON_DATA_LENGTH, Constants.DATA_LENGTH_HOST_COMMON);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : COMMON_DATA_LENGTH: " 
					+ internalMsg.getValue(HOST_COMMON_DATA.COMMON_DATA_LENGTH).getValue());
			
			// 64. PAN
			internalMsg.setValue(HOST_COMMON_DATA.PAN, pan); //value from getPan() method
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : PAN: " 
					+ internalMsg.getValue(HOST_COMMON_DATA.PAN).getValue());

			// 65. Processing Code
			if (originalMsg.hasField(3)) {
				internalMsg.setValue(HOST_COMMON_DATA.PROCESSING_CODE, originalMsg.getString(3));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : PROCESSING_CODE: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.PROCESSING_CODE).getValue());
			}

			// 66. Transaction Amount
			if (originalMsg.hasField(4)) { 
				internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_AMOUNT, originalMsg.getString(4));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_AMOUNT: "
						+ internalMsg.getValue(HOST_COMMON_DATA.TRANSACTION_AMOUNT).getValue());
			}

			// 67. Settlement Amount
			if (originalMsg.hasField(5)) { 
				internalMsg.setValue(HOST_COMMON_DATA.SETTLEMENT_AMOUNT, originalMsg.getString(5));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SETTLEMENT_AMOUNT: "
						+ internalMsg.getValue(HOST_COMMON_DATA.SETTLEMENT_AMOUNT).getValue());
			}

			// 68. Cardholder Billing Amount
			if (originalMsg.hasField(6)) { 
				internalMsg.setValue(HOST_COMMON_DATA.CARDHOLDER_BILLING_AMOUNT,
						originalMsg.getString(6));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CARDHOLDER_BILLING_AMOUNT: "
						+ internalMsg.getValue(HOST_COMMON_DATA.CARDHOLDER_BILLING_AMOUNT).getValue());
			}
			
			// [mqueja:09/23/2011] [Partial Reversal - revised logic]
			// [salvaro:10/26/2011] Revision: Set only the value for Cardholder Billing Amount 
			if(originalMsg.hasField(95)){

				if(originalMsg.getString(95).length() >= 36){
					String acqActualAmount = originalMsg.getString(95).substring(0,12);
					String issActualAmount = originalMsg.getString(95).substring(24, 36);

					// [mqueja:11/11/2011] [Partial Reversal - added checking of multicurrency]
					if(acqActualAmount.equals(issActualAmount)){
						internalMsg.setValue(HOST_COMMON_DATA.REPLACEMENT_CARDHOLDER_BILLING_AMT, acqActualAmount);
					} else {
						internalMsg.setValue(HOST_COMMON_DATA.REPLACEMENT_CARDHOLDER_BILLING_AMT, issActualAmount);
					}

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
							"REPLACEMENT_CARDHOLDER_BILLING_AMT " + internalMsg.getValue(HOST_COMMON_DATA.REPLACEMENT_CARDHOLDER_BILLING_AMT).getValue());
					
					internalMsg.setValue(HOST_COMMON_DATA.REPLACEMENT_ACQUIRER_AMT, acqActualAmount);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
							"REPLACEMENT_ACQUIRER_AMT " + internalMsg.getValue(HOST_COMMON_DATA.REPLACEMENT_ACQUIRER_AMT).getValue());
					
					internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.PARTIAL_REVERSAL_INDICATOR, Constants.PARTIAL_REVERSAL_INDICATOR_ON);
				
				}else{
					internalMsg.setValue(HOST_FEP_ADDITIONAL_INFO.PARTIAL_REVERSAL_INDICATOR, Constants.PARTIAL_REVERSAL_INDICATOR_OFF);
				}
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
						"PARTIAL REVERSAL FLAG " + internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.PARTIAL_REVERSAL_INDICATOR).getValue());
			}

			/*
			 * 69. Transaction Date
			 * 70. Transaction Time
			 */
			if (originalMsg.hasField(7)) {

//				internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_DATE, field7Sub1yyyymmdd);
//				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_DATE: "
//						+ internalMsg.getValue(HOST_COMMON_DATA.TRANSACTION_DATE).getValue());
				
				String field7_1_YYYYMMDD;
				String mmdd = originalMsg.getString(7).substring(0, 4);
				
				String mmdd2 = String.valueOf(ISODate.formatDate(Calendar.getInstance().getTime(), "MMdd"));
				int YYYY;
				
				if( mmdd2.equals(Constants.CUP_DECEMBER_31) || mmdd2.equals(Constants.CUP_JANUARY_01)){
					if (mmdd.compareToIgnoreCase(mmdd2) < 0) {
						YYYY = FepUtilities.getYear(mcpProcess, mmdd);
						field7_1_YYYYMMDD = (YYYY + 1) + mmdd;
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
								"[BanknetInternalFormatProcess]: Cross border transaction Date" + field7_1_YYYYMMDD);
						internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_DATE, field7_1_YYYYMMDD);

					}else if(mmdd.compareToIgnoreCase(mmdd2) > 0){

						field7_1_YYYYMMDD = FepUtilities.getYear(mcpProcess, mmdd) + mmdd;
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
								"[BanknetInternalFormatProcess]: Cross border transaction Date" + field7_1_YYYYMMDD);
						internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_DATE, field7_1_YYYYMMDD);
						
					}else{
						field7_1_YYYYMMDD = String.valueOf(ISODate.formatDate(Calendar.getInstance().getTime(), "yyyy")) + mmdd;
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
								"[BanknetInternalFormatProcess]: Same Transaction Date: " + field7_1_YYYYMMDD);
						internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_DATE, field7_1_YYYYMMDD);
					
					}
					
			
			}
				else{
						field7_1_YYYYMMDD = String.valueOf(ISODate.formatDate(Calendar.getInstance().getTime(), "yyyy")) + mmdd;
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
								"[BanknetInternalFormatProcess]: Same Transaction Date: " + field7_1_YYYYMMDD);
						internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_DATE, field7_1_YYYYMMDD);
			}
				
				
				internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_TIME, field7Sub2time);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_TIME: "
						+ internalMsg.getValue(HOST_COMMON_DATA.TRANSACTION_TIME).getValue());
			}

			// 71. System Trace Audit Number
			if (originalMsg.hasField(11)) {
				internalMsg.setValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER,
						originalMsg.getString(11));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SYSTEM_TRACE_AUDIT_NUMBER: "
						+ internalMsg.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue());
			}

			// 72 Local Transaction Time 
			if (originalMsg.hasField(12)) {
				internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME,
						originalMsg.getString(12));
			} else {
				internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME,
						ISODate.formatDate
						(new Date(), Constants.DATE_FORMAT_hhmmss_24H));
			}
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : LOCAL_TRANSACTION_TIME: "
					+ internalMsg.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME).getValue());

			// 73 Local Transaction Date 
			if (originalMsg.hasField(13)) {
				internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE,
						originalMsg.getString(13));
			} else {
				internalMsg.setValue(InternalFieldKey.HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE,
						ISODate.formatDate
						(new Date(), Constants.DATE_FORMAT_MMdd));
			}

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : LOCAL_TRANSACTION_DATE: "
					+ internalMsg.getValue(HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE).getValue());

			/* 74. Expiration Date
			 * [Lquirona 08-08-2011] Redmine Issue# 3208
			 * [csawi:20111005] [updated the implementations]
			 * [csawi:20111004] [change from orginalMsg.getstring(14) to expirationDate]
			 * [mqueja:20111117] [removed redundancy of getting the valu of expiration date]
			 */
			internalMsg.setValue(HOST_COMMON_DATA.EXPIRATION_DATE, getExpirationDate(originalMsg));
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : EXPIRATION_DATE " 
					+ internalMsg.getValue(HOST_COMMON_DATA.EXPIRATION_DATE).getValue()); 

			// 75. Settlement Date
			if (originalMsg.hasField(15)) {
				internalMsg.setValue(HOST_COMMON_DATA.SETTLEMENT_DATE, originalMsg.getString(15));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SETTLEMENT_DATE: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.SETTLEMENT_DATE).getValue());
			}
			
			// 76. Merchant Category Code
			if (originalMsg.hasField(18)) {
				mcc = originalMsg.getString(18);
				internalMsg.setValue(HOST_COMMON_DATA.MERCHANTE_CATAGORY_CODE,
						mcc);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : MERCHANTE_CATAGORY_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.MERCHANTE_CATAGORY_CODE).getValue());
			}

			// 77. Acquiring Institution Country Code
			if (originalMsg.hasField(19)) {
				internalMsg.setValue(HOST_COMMON_DATA.ACQUIRING_INSTITUTION_COUNTRY_CODE,
						originalMsg.getString(19));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : ACQUIRING_INSTITUTION_COUNTRY_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.ACQUIRING_INSTITUTION_COUNTRY_CODE).getValue());
			}

			// 78. Card Sequence Number
			if (originalMsg.hasField(23)) {
				internalMsg.setValue(HOST_COMMON_DATA.CARD_SEQUENCE_NUMBER, originalMsg.getString(23));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : CARD_SEQUENCE_NUMBER: "
						+ internalMsg.getValue(HOST_COMMON_DATA.CARD_SEQUENCE_NUMBER).getValue());
			}

			// 79. POS Condition Code
			if (originalMsg.hasField(25)) {
				internalMsg.setValue(HOST_COMMON_DATA.POS_CONDITION_CODE, originalMsg.getString(25));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : POS_CONDITION_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.POS_CONDITION_CODE).getValue());
			}

			// 80. Acquiring Institution ID Code
			if (originalMsg.hasField(32)) {
				internalMsg.setValue(HOST_COMMON_DATA.ACQUIRING_INSTITUTION_ID_CODE, ISOUtil.padleft
						(originalMsg.getString(32), HOST_COMMON_DATA.ACQUIRING_INSTITUTION_ID_CODE.length(),
								Constants.cPAD_0));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : ACQUIRING_INSTITUTION_ID_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.ACQUIRING_INSTITUTION_ID_CODE).getValue());
			}

			// 81. Forwarding Institution ID Code
			if (originalMsg.hasField(33)) {
				internalMsg.setValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE,
						originalMsg.getString(33));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : FORWARDING_INSTITUTION_ID_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.FORWARDING_INSTITUTION_ID_CODE).getValue());
			}

			// 82. Track 2 Data
			// csawi 20111007: added try-catch block 
			try{
				if (originalMsg.hasField(35)) {
					internalMsg.setValue(HOST_COMMON_DATA.TRACK_2_DATA,
							change2Binary(originalMsg.getString(35),false));
				}
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRACK_2_DATA: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.TRACK_2_DATA).getValue());
				
			} catch (Exception e) { 
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRACK_2_DATA "
						+ " Exception Occurred" + FepUtilities.getCustomStackTrace(e));           
				internalMsg.setValue(HOST_COMMON_DATA.TRACK_2_DATA, NO_SPACE);
			} // End of try-catch
			
			
			// 83. Retrieval Reference Number
			if (originalMsg.hasField(37)) {
				internalMsg.setValue(HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER, originalMsg.getString(37));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : RETRIEVAL_REF_NUMBER: "
						+ internalMsg.getValue(HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER).getValue());
			}

			// 84. Authorization ID Response
			if (originalMsg.hasField(38)) {
				internalMsg.setValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE,
						originalMsg.getString(38));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : AUTHORIZATION_ID_RESPONSE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE).getValue());
			}

			// 85. Response Code
			if (originalMsg.hasField(39)) {
				internalMsg.setValue(HOST_COMMON_DATA.RESPONSE_CODE, originalMsg.getString(39));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : RESPONSE_CODE: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.RESPONSE_CODE).getValue());
			}

			// 86. Card Acceptor Terminal ID
			if (originalMsg.hasField(41)) {
				internalMsg.setValue(HOST_COMMON_DATA.CARD_ACCEPTOR_TERMINAL_ID,
						originalMsg.getString(41));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : CARD_ACCEPTOR_TERMINAL_ID: "
						+ internalMsg.getValue(HOST_COMMON_DATA.CARD_ACCEPTOR_TERMINAL_ID).getValue());
			}

			// 87. Card Acceptor Identification Code
			if (originalMsg.hasField(42)) {
				internalMsg.setValue(HOST_COMMON_DATA.CARD_ACCEPTOR_IDENTIFICATION_CODE,
						originalMsg.getString(42));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : CARD_ACCEPTOR_IDENTIFICATION_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.CARD_ACCEPTOR_IDENTIFICATION_CODE).getValue());
			}

			// 88. Card Acceptor Name and Location
			if (originalMsg.hasField(43)) {
				internalMsg.setValue(HOST_COMMON_DATA.CARD_ACCEPTOR_NAME_AND_LOCATION,
						FepUtilities.changeSymbolToSpace(originalMsg.getString(43)));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
						"[BanknetInternalFormatProcess] : CARD_ACCEPTOR_NAME_AND_LOCATION (original): " + originalMsg.getString(43));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CARD_ACCEPTOR_NAME_AND_LOCATION (updated): "
						+ internalMsg.getValue(HOST_COMMON_DATA.CARD_ACCEPTOR_NAME_AND_LOCATION).getValue());
			}

			// 89. Track 1 Data
			if (originalMsg.hasField(45)) {
				internalMsg.setValue(HOST_COMMON_DATA.TRACK_1_DATA, originalMsg.getString(45));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRACK_1_DATA: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.TRACK_1_DATA).getValue());
			}

			// 90. Transaction Currency Code
			if (originalMsg.hasField(49)) {
				internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_CURRENCY_CODE,
						originalMsg.getString(49));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : TRANSACTION_CURRENCY_CODE: "
						+ internalMsg.getValue(HOST_COMMON_DATA.TRANSACTION_CURRENCY_CODE).getValue());
			}


			
			// 91. Settlement Currency code 
			if (originalMsg.hasField(50)) {
				internalMsg.setValue(HOST_COMMON_DATA.SETTLEMENT_CURRENCY_CODE,
						originalMsg.getString(50));
			}

			// 92. Org. Sys. Trace Number
			if (originalMsg.hasField(90)) {
				// 90.2 system trace audit number
				internalMsg.setValue(HOST_COMMON_DATA.ORG_SYS_TRACE_NO,
						field90Sub2);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : ORG_SYS_TRACE_NO: "
						+ internalMsg.getValue(HOST_COMMON_DATA.ORG_SYS_TRACE_NO).getValue());
			}

			// 93. Pin Value
			if (originalMsg.hasField(52)) {
				internalMsg.setValue(HOST_COMMON_DATA.PIN_VALUE, new String(originalMsg.getBytes(52)));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : PIN_VALUE: " 
						+ internalMsg.getValue(HOST_COMMON_DATA.PIN_VALUE).getValue());
			}

			// [mqueja:07262011] [updated for testing] 104. Additional Data
			internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.ADDITION_DATA,
					(originalMsg.hasField(48))
					? getField48(originalMsg) 
							: Constants.PAD_000 + Constants.SPACE);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ADDITION_DATA: " 
					+ internalMsg.getValue(InternalFieldKey.OPTIONAL_INFORMATION.ADDITION_DATA).getValue());

			// BKNMDS NW_Pec_Data (Shopping)
			if(Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3Sub1) 
					|| FLD18_MERCHANT_TYPE_6010.equals(mcc)
					//[rrabaya:11/28/2014]RM#5850 checking for ASI transaction : Start
					|| isPaymentASI
					|| isMoneySendPaymentASI
					|| isMoneySendPaymentAuth) { // [eotayde 12182014: RM 5838] Include checking for MoneySend Payment Auth
					//[rrabaya:11/28/2014]RM#5850 checking for ASI transaction : End
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SHOPPING ");
				
				if (mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Country Code: " + mcpProcess.getCountryCode());
					
					// 25. POS Entry Mode
					if(originalMsg.hasField(22)) {
						internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_ENTRY_MODE, originalMsg.getString(22));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
								"[BanknetInternalFormatProcess] : POS_ENTRY_MODE: " 
								+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_ENTRY_MODE).getValue());
					}
					
					// 97. Data Indicator
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.DATA_INDICATOR,
							SysCode.DATA_INDICATOR_BKH);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : DATA_INDICATOR: "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.DATA_INDICATOR).getValue());
					
					// 98. Network Original Data Length
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.NETWORK_ORIGINAL_DATA_LENGTH, 
							Constants.DATA_LENGTH_BKN_SHOPPING_ISSUING_NETWORK_ORIG_HK);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
								"[BanknetInternalFormatProcess] : NETWORK_ORIGINAL_DATA_LENGTH: "
								+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.NETWORK_ORIGINAL_DATA_LENGTH).getValue());
									
					/*
					 * [lsosuan 01/18/2011] [Redmine #2124] [HOST-ISS]BASE1 Cash Advance
					 * [lquirona] [DD 03/04/2011] [applicable to both HK and MY, removed checking for country code]
					 * [DD 03/11/2011] Redmine 1689: Changed setting for transaction type, removed old logic
					 * [lquirona 03 15 2011 Redmine 2184 Use private method to set transaction type]
					 */
					String transType = getTransactionType(originalMsg);
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.TRANSACTION_TYPE, transType);
						
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : mti: " + mti);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field 3.1: " + field3Sub1);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_TYPE " 
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.TRANSACTION_TYPE).getValue());
					
					// 99. Message Type
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.MESSAGE_TYPE, mti);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : MESSAGE_TYPE "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.MESSAGE_TYPE).getValue());

					// 100. Conversion Rate
					if(originalMsg.hasField(9)) {
						internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.CONVERSION_RATE,
								originalMsg.getString(9));
					} else if(originalMsg.hasField(10)){
						internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.CONVERSION_RATE,
								originalMsg.getString(10));
					}
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : CONVERSION_RATE "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.CONVERSION_RATE).getValue());


					// 102. POS Capture Mode
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_CAPTURE_CODE,
							(originalMsg.hasField(26))
							    ? originalMsg.getString(26)
							    : Constants.SPACE);

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : POS_CAPTURE_CODE "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_CAPTURE_CODE).getValue());
					
					// 103. Additional Response Data
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_RESPONSE_DATA,
							(originalMsg.hasField(44))
							    ? originalMsg.getString(44) 
							    : Constants.PAD_00 + Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADDITION_RESPONSE_DATA "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_RESPONSE_DATA).getValue());
					
					// 104. Additional Data
					// [salvaro:20111025] Edited setting of value for HK
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_DATA,
							(originalMsg.hasField(48))
							    ? ISOUtil.padleft(getField48(originalMsg).length()+ "", 3,cPAD_0) + getField48(originalMsg)
							    		: Constants.PAD_000 + Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADDITION_DATA "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_DATA).getValue());
					
					// DD Update 10/26/2010: Removed Additional Amounts Field
					// 106. Advice Reason Code
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADVICE_REASON_CODE,
							(originalMsg.hasField(60))
							   ? originalMsg.getString(60) 
							   : Constants.PAD_00 + Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADVICE_REASON_CODE "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ADVICE_REASON_CODE).getValue());
					
					/*
					 * 107. POS Information Data
					 * [mquines: 12/09/2011] [changed setting of DE61 to internal format due to null values received]
					 */
					if(originalMsg.hasField(61)){
						ISOMsg isoMsg61 =  new ISOMsg();
						isoMsg61 = (ISOMsg) originalMsg.getValue(61);
						String field61Value = NO_SPACE;
						
						for(int index = 1; index < 15; index++) {
							//Added if hasField() to prevent null pointer exception
							if(isoMsg61.hasField(index)){
								field61Value += isoMsg61.getString(index);	
							}else{
								// Error handling: If field does not exist set spaces. 
								// Number of spaces is based on the data representation
								if(index == 1 || index == 2 || index == 3 || index == 4 || index == 5 
										|| index == 6 || index == 7 || index == 8 || index == 9 
										|| index == 10 || index == 11){
									field61Value += SPACE;
								}else if(index == 12 ){
									field61Value += SPACE_00;
								}else if(index == 13){
									field61Value += SPACE_000;
								}else if(index == 14){
									field61Value += SPACE_0000000000;
								}// End of inner if-else
							}// End of if-else
						}// End of for
						
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetInternalFormatProcess] : field61Value: " + field61Value);
						//[mquines:12/09/2011] Add checking of F61 length to prevent Type Not Matched Exception
						if(field61Value.length() <= 26){
					/**[rrabaya 09122014] RM# 5786 [ACSA] MasterCard : Debug Logging - Null value encounter for Field 61 : Start*/
					//Change the setting from BKNMDSFieldKey to BKNMDSHKFieldKey
							internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_INFORMATION_DATA, field61Value);
						}else{
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_WARNING,"[BanknetInternalFormatProcess] : field61Value is > 26: " + field61Value);
							internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_INFORMATION_DATA, PAD_00 + SPACE);
						}// End if if-else

					} else {
						internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_INFORMATION_DATA, PAD_00 + SPACE);
					}// End of if-else
					/**[rrabaya 09122014] RM# 5786 [ACSA] MasterCard : Debug Logging - Null value encounter for Field 61 : End*/

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : POS_INFORMATION_DATA "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.POS_INFORMATION_DATA).getValue());
					
					// 108. Banknet Data
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.BANKNET_DATA_MDS_DATA,
							(originalMsg.hasField(63))
							   ? originalMsg.getString(63) 
							   : Constants.SPACE);

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : BANKNET_DATA_MDS_DATA "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.BANKNET_DATA_MDS_DATA).getValue());
					
					// 109. Original Data Element
					internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ORIGINAL_DATA_ELEMENT,
							(originalMsg.hasField(90))
							   ? originalMsg.getString(90) 
							   : Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ORIGINAL_DATA_ELEMENT "
							+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.ORIGINAL_DATA_ELEMENT).getValue());
					
					//[rrabaya:11/28/2014]RM#5850 Reminder for MasterCard April 2014 Compliance - Global 555 Payment Account Status : Start
					//Modify setting for field 48.77(Payment Transaction Type)
					//138. Payment Transaction Type
					if (isPaymentASI || isMoneySendPaymentASI
							|| isMoneySendPaymentAuth) { // [eotayde 12182014: RM 5838] Include checking for MoneySend Payment Auth
						
						if (originalMsg.hasField(48)) {
							ISOMsg isoField48 = (ISOMsg) originalMsg.getValue(48);
							String paymentTransactionType = isoField48.getString(77);
							internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.PAYMENT_TRANSACTION_TYPE, paymentTransactionType);
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] :"
									+ " PAYMENT_TRANSACTION_TYPE: "
									+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.PAYMENT_TRANSACTION_TYPE).getValue());
						} else {
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] :"
									+ " has no DE 48");
						}
					}
					
					//139. Member Defined Data
					if(isMoneySendPaymentASI
							|| isMoneySendPaymentAuth) { // [eotayde 12182014: RM 5838] Include checking for MoneySend Payment Auth 
						// [eotayde 12232014] Log MEMBER-DEFINED-DATA
						internalMsg.setValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.MEMBER_DEFINED_DATA, originalMsg.getString(124));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : "
								+ "MEMBER_DEFINED_DATA: "
								+ internalMsg.getValue(BKNMDSHKFieldKey.BANKNET_MDS_HK.MEMBER_DEFINED_DATA).getValue());
					}
						MasterCardMoneySendParser moneySendParser = new MasterCardMoneySendParser();
						HashMap<String,String> field108 = new HashMap<String,String>();
						if(originalMsg.hasField(108)){
							field108 = moneySendParser.parseMoneySendRefData(originalMsg.getString(108));
							internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.MONEYSEND_REFERENCE_DATA, field108.toString());
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : "
									+ internalMsg.getValue(OPTIONAL_INFORMATION.MONEYSEND_REFERENCE_DATA).getValue());
						}
					//[rrabaya:11/28/2014]RM#5850 Reminder for MasterCard April 2014 Compliance - Global 555 Payment Account Status : End
					
				} else {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Country Code: " + mcpProcess.getCountryCode());
					
					// 25. POS Entry Mode
					if(originalMsg.hasField(22)) {
						internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.POS_ENTRY_MODE, originalMsg.getString(22));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
								"[BanknetInternalFormatProcess] : POS_ENTRY_MODE: " 
								+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.POS_ENTRY_MODE).getValue());
					}
					
					// 97. Data Indicator
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.DATA_INDICATOR,
							SysCode.DATA_INDICATOR_BKN);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : DATA_INDICATOR: "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.DATA_INDICATOR).getValue());
					
					// 98. Network Original Data Length
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.NETWORK_ORIGINAL_DATA_LENGTH, 
							Constants.DATA_LENGTH_BKN_SHOPPING_ISSUING_NETWORK_ORIG);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, 
							"[BanknetInternalFormatProcess] : NETWORK_ORIGINAL_DATA_LENGTH: "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.NETWORK_ORIGINAL_DATA_LENGTH).getValue());
									
					
					//[lsosuan 01/18/2011] [Redmine #2124] [HOST-ISS]BASE1 Cash Advance  
					// [lquirona] [DD 03/04/2011] [applicable to both HK and MY, removed checking for country code]				
					// [DD 03/11/2011] Redmine 1689: Changed setting for transaction type, removed old logic
					// [lquirona 03 15 2011 Redmine 2184 Use private method to set transaction type]
					String transType = getTransactionType(originalMsg);
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.TRANSACTION_TYPE, transType);
						
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MTI: " + mti);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DE 3 SE 1: " + field3Sub1);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_TYPE " 
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.TRANSACTION_TYPE).getValue());
					
					// 99. Message Type
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE, mti);
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : MESSAGE_TYPE "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.MESSAGE_TYPE).getValue());

					// 100. Conversion Rate
					if(originalMsg.hasField(9)) {
						internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.CONVERSION_RATE,
								originalMsg.getString(9));
					} else if(originalMsg.hasField(10)){
						internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.CONVERSION_RATE,
								originalMsg.getString(10));
					}
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : CONVERSION_RATE "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.CONVERSION_RATE).getValue());


					// 102. POS Capture Mode
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.POS_CAPTURE_CODE,
							(originalMsg.hasField(26))
							    ? originalMsg.getString(26)
							    : Constants.SPACE);

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : POS_CAPTURE_CODE "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.POS_CAPTURE_CODE).getValue());
					
					// 103. Additional Response Data
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.ADDITION_RESPONSE_DATA,
							(originalMsg.hasField(44))
							    ? originalMsg.getString(44) 
							    : Constants.PAD_00 + Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADDITION_RESPONSE_DATA "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.ADDITION_RESPONSE_DATA).getValue());
					
					// 104. Additional Data
					internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.ADDITION_DATA,
							(originalMsg.hasField(48))
							    ? getField48(originalMsg) 
							    : Constants.PAD_000 + Constants.SPACE);

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADDITION_DATA "
							+ internalMsg.getValue(InternalFieldKey.OPTIONAL_INFORMATION.ADDITION_DATA).getValue());
					
					// DD Update 10/26/2010: Removed Additional Amounts Field
					// 106. Advice Reason Code
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.ADVICE_REASON_CODE,
							(originalMsg.hasField(60))
							   ? originalMsg.getString(60) 
							   : Constants.PAD_00 + Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ADVICE_REASON_CODE "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.ADVICE_REASON_CODE).getValue());
					
					/*
					 * 107. POS Information Data
					 * [mqueja: 12/7/2011] [changed setting of DE61 to internal format due to null values received]
					 */
					if(originalMsg.hasField(61)){
						ISOMsg isoMsg61 =  new ISOMsg();
						isoMsg61 = (ISOMsg) originalMsg.getValue(61);
						//[mquines:12/09/2011] Change value of field61Value from SPACE to NO_SPACE
						String field61Value = NO_SPACE;
						
						for(int index = 1; index < 15; index++) {
							//[mquines:12/09/2011] Changed the implementation. Added if hasField() to prevent null pointer exception
							if(isoMsg61.hasField(index)){
								field61Value += isoMsg61.getString(index);	
							}else{
								//[mquines:12/09/2011] Error handling: If field does not exist set spaces. 
								// Number of spaces is based on the data representation
								if(index == 1 || index == 2 || index == 3 || index == 4 || index == 5 
										|| index == 6 || index == 7 || index == 8 || index == 9 
										|| index == 10 || index == 11){
									field61Value += SPACE;
								}else if(index == 12 ){
									field61Value += SPACE_00;
								}else if(index == 13){
									field61Value += SPACE_000;
								}else if(index == 14){
									field61Value += SPACE_0000000000;
								}// End of inner if-else
							}// End of if-else
						}// End of for
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetInternalFormatProcess] : field61Value: " + field61Value);
						//[mquines:12/09/2011] Add checking of F61 length to prevent Type Not Matched Exception
						if(field61Value.length() <= 26){
							internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.POS_INFORMATION_DATA, field61Value);
						}else{
							mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_WARNING,"[BanknetInternalFormatProcess] : field61Value is > 26: " + field61Value);
							internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.POS_INFORMATION_DATA, PAD_00 + SPACE);
						}// End if if-else
					} else {
						internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.POS_INFORMATION_DATA, PAD_00 + SPACE);
					}// End of if-else

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : POS_INFORMATION_DATA "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.POS_INFORMATION_DATA).getValue());
					
					// 108. Banknet Data
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA,
							(originalMsg.hasField(63))
							   ? originalMsg.getString(63) 
							   : Constants.SPACE);

					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : BANKNET_DATA_MDS_DATA "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.BANKNET_DATA_MDS_DATA).getValue());
					
					// 109. Original Data Element
					internalMsg.setValue(BKNMDSFieldKey.BANKNET_MDS.ORIGINAL_DATA_ELEMENT,
							(originalMsg.hasField(90))
							   ? originalMsg.getString(90) 
							   : Constants.SPACE);
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
							"[BanknetInternalFormatProcess] : ORIGINAL_DATA_ELEMENT "
							+ internalMsg.getValue(BKNMDSFieldKey.BANKNET_MDS.ORIGINAL_DATA_ELEMENT).getValue());
				}
			}// End of if shopping transaction
			
			// [salvaro:20111028] Added Setting of Transaction Fee
			if(originalMsg.hasField(28)){	 
				internalMsg.setValue(HOST_COMMON_DATA.TRANSACTION_FEE_ACQUIRER_AMT, 
						originalMsg.getString(28));
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"TRANSACTION_FEE_ACQUIRER_AMT " + internalMsg.getValue(HOST_COMMON_DATA.TRANSACTION_FEE_ACQUIRER_AMT));
			}

			// ATM_CASH_ADVANCE NW_Pec_DATA
			if(!FLD18_MERCHANT_TYPE_6010.equals(mcc) && (Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3Sub1)
					|| Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3Sub1) 
					//[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASI : Start
                    || (Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3Sub1))
                    	&& (!(isPaymentASI || isMoneySendPaymentASI
                    			|| isMoneySendPaymentAuth)) // [eotayde 12182014: RM 5838] Include checking for MoneySend Payment Auth
                    //[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASI : End
                    || Constants.FLD3_1_TRANSACTION_TYPE_30.equals(field3Sub1))) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CASHING");

				//110. Data Indicator
				internalMsg.setValue(ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.DATA_INDICATOR, 
						SysCode.DATA_INDICATOR_ATM);
				//111. Network Original Data Length
				internalMsg.setValue(
						ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.NETWORK_ORIGINAL_DATA_LENGTH,
						Constants.DATA_LENGTH_ATM_CASHADVANCE_NETWORK_ORIG);
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : DATA_INDICATOR "
						+ internalMsg.getValue(ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.DATA_INDICATOR).getValue());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : NETWORK_ORIGINAL_DATA_LENGTH "
						+ internalMsg.getValue(
						ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.NETWORK_ORIGINAL_DATA_LENGTH).getValue());
			
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
						"[BanknetInternalFormatProcess] : Country Code: "
						+ mcpProcess.getCountryCode());
				
				/*
				 * 112. Transaction Type
				 * [lsosuan 01/18/2011] [Redmine #2124] [HOST-ISS]BASE1 Cash Advance
				 * [emaranan:03/14/2011] [Redmine#1689] Update setting Transaction type 
				 * [lquirona 03 15 2011 Redmine 2184 Use private method to set transaction type]
				 */
				String transType = getTransactionType(originalMsg);
				internalMsg.setValue(ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.TRANSACTION_TYPE, transType);
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field 3.1: " + field3Sub1);
                mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
                        "[BanknetInternalFormatProcess] : TRANSACTION_TYPE: " + internalMsg.getValue(
                                ATMCashAdvanceFieldKey.ATM_CASH_ADVANCE.TRANSACTION_TYPE).getValue());
					
				// 122. ATM TRANS SEQ
				internalMsg.setValue(ATM_CASH_ADVANCE.ATM_TRANS_SEQ, PAD_00000000);
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ATM_TRANS_SEQ: " 
						+ internalMsg.getValue(ATM_CASH_ADVANCE.ATM_TRANS_SEQ).getValue());
				
				// 138. Payment Transaction Type
				if(originalMsg.hasField(48)){					
					inner = (ISOMsg)originalMsg.getValue(48);
					if(inner.hasField(77)){
						internalMsg.setValue(ATM_CASH_ADVANCE.PAYMENT_TRANSACTION_TYPE, inner.getString(77));
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " 
                                + "PAYMENT_TRANSACTION_TYPE: " 
								+ internalMsg.getValue(ATM_CASH_ADVANCE.PAYMENT_TRANSACTION_TYPE).getValue());
					}
				}
				
				// 139. Member Defined Data
				if(originalMsg.hasField(124)){
					internalMsg.setValue(ATM_CASH_ADVANCE.MEMBER_DEFINED_DATA, originalMsg.getString(124));
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MEMBER_DEFINED_DATA: " 
							+ internalMsg.getValue(ATM_CASH_ADVANCE.MEMBER_DEFINED_DATA).getValue());
				}
				/*
				 * 107. POS Information Data
				 * [mqueja: 12/7/2011] [changed setting of DE61 to internal format due to null values received]
				 */
				if(originalMsg.hasField(61)){
					ISOMsg isoMsg61 =  new ISOMsg();
					isoMsg61 = (ISOMsg) originalMsg.getValue(61);
					//[mquines:12/09/2011] Change value of field61Value from SPACE to NO_SPACE
					String field61Value = NO_SPACE;
					
					for(int index = 1; index < 15; index++) {
						//[mquines:12/09/2011] Changed the implementation. Added if hasField() to prevent null pointer exception
						if(isoMsg61.hasField(index)){
							field61Value += isoMsg61.getString(index);	
						}else{
							//[mquines:12/09/2011] Error handling: If field does not exist set spaces. 
							// Number of spaces is based on the data representation
							if(index == 1 || index == 2 || index == 3 || index == 4 || index == 5 
									|| index == 6 || index == 7 || index == 8 || index == 9 
									|| index == 10 || index == 11){
								field61Value += SPACE;
							}else if(index == 12 ){
								field61Value += SPACE_00;
							}else if(index == 13){
								field61Value += SPACE_000;
							}else if(index == 14){
								field61Value += SPACE_0000000000;
							}// End of inner if-else
						}// End of if-else
					}// End of for
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,"[BanknetInternalFormatProcess] : field61Value: " + field61Value);
					//[mquines:12/09/2011] Add checking of F61 length to prevent Type Not Matched Exception
					if(field61Value.length() <= 26){
						internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.POS_INFORMATION_DATA, field61Value);
					}else{
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_WARNING,"[BanknetInternalFormatProcess] : field61Value is > 26: " + field61Value);
						internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.POS_INFORMATION_DATA, PAD_00 + SPACE);
					}// End if if-else
				} else {
					internalMsg.setValue(InternalFieldKey.OPTIONAL_INFORMATION.POS_INFORMATION_DATA, PAD_00 + SPACE);
				}// End of if-else

			}// End of if cash transaction
			/*}  [lquirona] [DD 03/04/2011] [applicable to hk] */

            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : IC_FLAG: " 
					+ internalMsg.getValue(CONTROL_INFORMATION.IC_FLAG).getValue());
			
			// IC Info Part
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : IC INFORMATION PART");
			if(SysCode.IC_INFO_EXIST.equals(internalMsg.getValue(CONTROL_INFORMATION.IC_FLAG).getValue())) {
				//157. IC Authentication Implementation Division
				if((SysCode.MTI_0100.equals(mti) 
						|| SysCode.MTI_0101.equals(mti) 
						|| SysCode.MTI_0400.equals(mti)) 
					&& 
						(Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3Sub1) 
						|| Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3Sub1) 
                        // [emaranan 02/09/2011] [Redmine #2196] [BNK-ISS] Cash Advance using IC Transaction
                        || Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3Sub1) 
						|| Constants.FLD3_1_TRANSACTION_TYPE_30.equals(field3Sub1))) {
					internalMsg.setValue(IC_INFORMATION.IC_AUTHENTICATION_IMPLEMENTATION_DIVISION, 
							SysCode.IC_INFO_EXIST);
				} else {
					internalMsg.setValue(IC_INFORMATION.IC_AUTHENTICATION_IMPLEMENTATION_DIVISION, 
							SysCode.IC_INFO_NOT_EXIST);
				}
				if(originalMsg.hasField(55)){ 
					// [Christian Paul Calanog: 07/06/2010] [Redmine #536] [get position 1-3]
					internalMsg.setValue(IC_INFORMATION.IC_REQUEST_DATA,
							new String(originalMsg.getBytes(55)));
				}
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] :" +
						" : IC_AUTHENTICATION_IMPLEMENTATION_DIVISION: "
						+ internalMsg.getValue(IC_INFORMATION.IC_AUTHENTICATION_IMPLEMENTATION_DIVISION).getValue());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,	"[BanknetInternalFormatProcess] : IC_REQUEST_DATA: "
						+ internalMsg.getValue(IC_INFORMATION.IC_REQUEST_DATA).getValue());
			}// If IC Info EXISTS
			
			/*
			 * WITH IC SECOND ICVV CHECK 
			 * Lquirona 20110623 Update for IC Transaction w/o F55 from Partial Grade Acquirers
			 */
			if(SysCode.IC_INFO_NOT_EXIST.equals(internalMsg.getValue(CONTROL_INFORMATION.IC_FLAG).getValue())){
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SECOND ICVV Condition Check");
				if((originalMsg.hasField(3)) && (originalMsg.hasField(22)) && originalMsg.hasField(61)){
					if(((Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3Sub1))
							|| (Constants.FLD22_1_PAN_ENTRY_MODE_05.equals(field22))
							|| (Constants.FLD61_10_AUTHORIZED_LEVEL_1_CAT).equals(field61Sub10))
							||
							((Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3Sub1))
									|| (Constants.FLD22_1_PAN_ENTRY_MODE_05.equals(field22))
									|| (Constants.FLD61_10_NOT_A_CAT_TRANSACION_0).equals(field61Sub10))){
						internalMsg.setValue(CONTROL_INFORMATION.SECOND_ICVV_CHECK, SysCode.WITH_SECOND_ICVV_CHECK);
						mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field3Sub1: " + field3Sub1 + " field22Sub1: " + field22
								+ " field61Sub10: " + field61Sub10);
						
					}// For Partial Grade
					else{
						internalMsg.setValue(CONTROL_INFORMATION.SECOND_ICVV_CHECK, SysCode.WITHOUT_SECOND_ICVV_CHECK);
					}// Fallback Magnetic and other cases without ICVV checking
					
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : SECOND ICVV CHECK FLAG: " 
							+ internalMsg.getValue(CONTROL_INFORMATION.SECOND_ICVV_CHECK).getValue());
				}// End of Second ICVV Check
			}// If IC Info NOT EXIST

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetInternalFormatProcess] : THREE_D_SECURE_FLAG: "
					+ internalMsg.getValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG).getValue());
			
			// 3d secure info
			if(SysCode.THREED_SECURE_WITHIN.equals(
					internalMsg.getValue(CONTROL_INFORMATION.THREE_D_SECURE_FLAG).getValue())) {
				// 167. 3D Secure Flag
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.THREE_D_SECURE_FLAG, 
						(SysCode.MTI_0100.equals(mti) 
						&& Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3Sub1))
						    ? SysCode.THREED_SECURE_WITHIN 
						    : SysCode.THREED_SECURE_WITHOUT);

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : THREE_D_SECURE_FLAG: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.THREE_D_SECURE_FLAG).getValue());
				
				// 170. Transaction Identifier (XID)
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.TRANSACTION_IDENTIFIER, 
						(originalMsg.hasField(48) && (null != field48Sub44)) 
							? field48Sub44
							: Constants.SPACE); 

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_IDENTIFIER: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.TRANSACTION_IDENTIFIER).getValue());
				
				// 171. Transaction Status
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.TRANSACTION_STATUS, 
						(field48Sub42.endsWith(Constants.FLD48_42_ELECTRONIC_COMMERCE_INDICATOR_212))
							? Constants.TRANSACTION_STATUS_AUTHENTICATION_SUCESSFUL 
					  : (field48Sub43.startsWith(Constants.TRANSACTION_STATUS_h)
							? Constants.TRANSACTION_STATUS_ATTEMPTS_PROCESSING_PERFORMED 
							: Constants.TRANSACTION_STATUS_AUTHENTICATION_FAILED));

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_STATUS: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.TRANSACTION_STATUS).getValue());
				
				// 172. CAVV Algorithm
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.CAVV_ALGORITHM, 
						(null != field48Sub44 && field48Sub43.startsWith(Constants.FLD48_43_ELECTRONIC_COMMERCE_INDICATOR_8)) 
							? Constants.CAVV_ALGORITHM_CVV : (field48Sub43.startsWith(Constants.FLD48_43_ELECTRONIC_COMMERCE_INDICATOR_8) && null == field48Sub44) 
							? Constants.CAVV_ALGORITHM_CVV_WITH_ATN : Constants.CAVV_ALGORITHM_HMAC);

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : CAVV_ALGORITHM: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.CAVV_ALGORITHM).getValue());
				
				
				// 173. CAVV/AVV
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.CAVV_AAV, 
						(field48Sub43.startsWith(Constants.FLD48_43_ELECTRONIC_COMMERCE_INDICATOR_8))
						? field48Sub43_2_20 : field48Sub43_1_28);
				String trans_status = internalMsg.getValue(
						THREED_D_SECURE_INFORMATION.TRANSACTION_STATUS).getValue();
				
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,	"[BanknetInternalFormatProcess] : CAVV_AAV: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.CAVV_AAV).getValue());
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,	"[BanknetInternalFormatProcess] : TRANSACTION_STATUS: "
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.TRANSACTION_STATUS).getValue());
				
				// 174. ECI(Electronic Commerce Indicator)
				internalMsg.setValue(THREED_D_SECURE_INFORMATION.ECI, 
						(Constants.TRANSACTION_STATUS_AUTHENTICATION_SUCESSFUL.equals(trans_status)) 
								? Constants.FLD60_8_ADDITIONAL_POS_INFORMATION_05 
						: (Constants.TRANSACTION_STATUS_ATTEMPTS_PROCESSING_PERFORMED.equals(trans_status)) 
								? Constants.FLD60_8_ADDITIONAL_POS_INFORMATION_06 
						: (Constants.TRANSACTION_STATUS_AUTHENTICATION_FAILED.equals(trans_status)) 
								? Constants.FLD60_8_ADDITIONAL_POS_INFORMATION_07
								: "");

				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : ECI: " 
						+ internalMsg.getValue(THREED_D_SECURE_INFORMATION.ECI).getValue());
				
			}

			// mapACE_Info_Part
			if(originalMsg.hasField(51)) {
				internalMsg.setValue(ACE_INFORMATION.RECON_CURRENCY_CODE, originalMsg.getString(51));
			}

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : RECON_CURRENCY_CODE: "
					+ internalMsg.getValue(ACE_INFORMATION.RECON_CURRENCY_CODE).getValue());
			
			/*
			 * [Christian Paul Calanog: 07/13/2010] [Redmine #602]
			 * [length of fields when creating InternalFormat messages from ISOMsg]
			 */
		} catch (TypeNotMatchedException e) {
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_ILLEGAL_PARAM);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_ILLEGAL_PARAM, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : createInternalFormatFromISOMsg() :"
					+ " TypeNotMatchedException Occured" + FepUtilities.getCustomStackTrace(e));
			return internalMsg;
		} catch (ISOException e) {
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SUBFIELD);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_SUBFIELD, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : createInternalFormatFromISOMsg() :"
					+ " ISOException Occured" + FepUtilities.getCustomStackTrace(e));
			return internalMsg;
		} catch (Exception e) { 
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2213);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, SERVER_SYSLOG_INFO_2213, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : createInternalFormatFromISOMsg() :"
					+ " Exception Occured" + FepUtilities.getCustomStackTrace(e));           
			return internalMsg;
		} // End of try-catch
		
		return internalMsg;
	}// End of createInternalFormatFromISOMsg()

	/**
	 * Create the ISOMsg according to the InternalFormat.
	 * Refer to SS0106 Message Interface Item Editing Definition(FEXC001 FINF002 External Message)
	 * @param internalMsg The Internal Format
	 * @return ISOMsg The created ISOMsg
	 */
	public ISOMsg createISOMsgFromInternalFormat(InternalFormat internalMsg) {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetInternalFormatProcess] : createISOMsgFromInternalFormat() ");
		
		String transCode = "";
		String mti;
		
		try {
			transCode = internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
			// [emaranan:02/24/2011] [Redmine#2228] [ACQ] SS documents update  
            mti = (transCode.startsWith(Constants.PREFIX_TRANS_CODE_FILE_UPDATE_REQ)) 
                          ? SysCode.MTI_0302 : 
			      (transCode.startsWith(Constants.PREFIX_TRANS_CODE_NETWORK_MANAGEMENT)) 
                          ? SysCode.MTI_0800 : 
                  (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REQ_1)) 
                          ? SysCode.MTI_0100 : 
                  (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REQ_4)) 
                          ? SysCode.MTI_0100 : 
                  (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1)) 
                          ? SysCode.MTI_0400 : 
                  (transCode.startsWith(Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE)) 
                          ? SysCode.MTI_0400 
				      : NO_SPACE;
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : TRANSACTION_CODE : " 
					+ transCode);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MTI : " + mti);

			if(SysCode.MTI_0100.equals(mti) || SysCode.MTI_0400.equals(mti)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MTI is either 0100/0400"); 
				return businessMessage(internalMsg,mti);
			} else if(SysCode.MTI_0302.equals(mti) || SysCode.MTI_0800.equals(mti)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : MTI is either 0302/0800");
				return controlMessage(internalMsg,mti,transCode);
			}
		} catch (ISOException e) {
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, CLIENT_SYSLOG_SUBFIELD);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, CLIENT_SYSLOG_SUBFIELD, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : createISOMsgFromInternalFormat() :"
					+ " ISOException Occured" + FepUtilities.getCustomStackTrace(e));
			return null;
		} catch (Exception e) { 
			args[0] = e.getMessage();
			sysMsgID = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, SERVER_SYSLOG_INFO_2213);
			mcpProcess.writeAppLog(sysMsgID, Level.ERROR, SERVER_SYSLOG_INFO_2213, args);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : createISOMsgFromInternalFormat() :"
					+ " Exception Occured" + FepUtilities.getCustomStackTrace(e));           
			return null;
		} // End of try-catch
		return null;
	}// End of createISOMsgFromInternalFormat()

	/**
	 * Create the ISOMsg according to the InternalFormat. (MTI 0302 and MTI 0800)
	 * Refer to SS0106 Message Interface Item Editing Definition(FEXC001 FINF002 External Message)
	 * @param internalMsg The Internal Format
	 * @param mti The Message Type Identifier
	 * @param transCode The transaction code
	 * @return ISOMsg The created ISOMsg
	 * @throws ISOException
	 */
	private ISOMsg controlMessage(InternalFormat internalMsg, String mti, String transCode) throws ISOException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : controlMessage() "); 
		ISOMsg isoMsg = new ISOMsg();
		isoMsg.setMTI(mti);
		isoMsg.set(11, internalMsg.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue());

		if(SysCode.MTI_0302.equals(mti)) {
			isoMsg.set(2,change2Binary(internalMsg.getValue(HOST_COMMON_DATA.PAN).getValue(),true));
		}
		isoMsg.recalcBitMap();
		
		return isoMsg;
	}// End of controlMessage()

	/**
	 * Create the ISOMsg according to the InternalFormat. (MTI 0100 and MTI 0400)
	 * Refer to SS0106 Message Interface Item Editing Definition(FEXC001 FINF002 External Message)
	 * @param internalMsg The Internal Format
	 * @param mti The Message Type Identifier
	 * @return ISOMsg The created ISOMsg
	 * @throws IllegalParamException 
	 * @throws AeonDBException 
	 */
	private ISOMsg businessMessage(InternalFormat internalMsg, String mti) throws ISOException, AeonDBException, 
            IllegalParamException {
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : businessMessage()"); 

		ISOMsg isoMsg;
		Collection<String> keySet;
		String internalValue;
		int[] unsetVariableBusinessMsg = {39, 90, 35, 45, 55};
		String strTrack2 = null;

		isoMsg = new ISOMsg();
		isoMsg.setMTI(mti);
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetInternalFormatProcess] MTI set in businessMessage: " + isoMsg.getMTI());

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
				"[BanknetInternalFormatProcess] : Setting keys and values in hashtable");
        
        String transCode = internalMsg.getValue(HOST_FEP_ADDITIONAL_INFO.TRANSACTION_CODE).getValue();
		Hashtable<String, IInternalKey> fieldList = new Hashtable<String, IInternalKey>();
		fieldList.put("2",HOST_COMMON_DATA.PAN);
		fieldList.put("3",HOST_COMMON_DATA.PROCESSING_CODE);
		fieldList.put("4",HOST_COMMON_DATA.TRANSACTION_AMOUNT);
		fieldList.put("6", HOST_COMMON_DATA.CARDHOLDER_BILLING_AMOUNT);
		
		//[emaranan:02/25/2011] [Redmine#2210] [ACQ] STAN value in MTI 0400 Processing
		fieldList.put("12",HOST_COMMON_DATA.LOCAL_TRANSACTION_TIME);
		fieldList.put("13",HOST_COMMON_DATA.LOCAL_TRANSACTION_DATE);
		fieldList.put("14",HOST_COMMON_DATA.EXPIRATION_DATE);
		fieldList.put("18",HOST_COMMON_DATA.MERCHANTE_CATAGORY_CODE);
		fieldList.put("22",OPTIONAL_INFORMATION.POS_ENTRY_MODE);
		fieldList.put("23",HOST_COMMON_DATA.CARD_SEQUENCE_NUMBER);
		fieldList.put("32", HOST_COMMON_DATA.ACQUIRING_INSTITUTION_ID_CODE);
		
		// [Ma Katrina Camille E Liwanag: 09/10/2010] [Redmine #969] [Check how request message is edited]
		fieldList.put("37",HOST_COMMON_DATA.RETRIEVAL_REF_NUMBER);
		fieldList.put("38",HOST_COMMON_DATA.AUTHORIZATION_ID_RESPONSE);
		fieldList.put("39",HOST_COMMON_DATA.RESPONSE_CODE);//off in 0100
		fieldList.put("41",HOST_COMMON_DATA.CARD_ACCEPTOR_TERMINAL_ID);
		fieldList.put("42",HOST_COMMON_DATA.CARD_ACCEPTOR_IDENTIFICATION_CODE);
		fieldList.put("43",HOST_COMMON_DATA.CARD_ACCEPTOR_NAME_AND_LOCATION); //check external doc
		
		// [salvaro:20111025] Edited setting of Field 44 for HK
		if(mcpProcess.getCountryCode().equals(Constants.HONGKONG)) {
			fieldList.put("44",BKNMDSHKFieldKey.BANKNET_MDS_HK.ADDITION_RESPONSE_DATA);
		}else{
			fieldList.put("44",BKNMDSFieldKey.BANKNET_MDS.ADDITION_RESPONSE_DATA);
		}
		
		fieldList.put("45",HOST_COMMON_DATA.TRACK_1_DATA); //off in 0400
		fieldList.put("48",OPTIONAL_INFORMATION.ADDITION_DATA);
		fieldList.put("49",HOST_COMMON_DATA.TRANSACTION_CURRENCY_CODE);
		fieldList.put("55",IC_INFORMATION.IC_REQUEST_DATA);		
		
		keySet = (Collection<String>) fieldList.keySet();
		internalValue = NO_SPACE;
		for (String key : keySet) {
			internalValue = internalMsg.getValue(fieldList.get(key)).getValue();
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] :" +
					" Field : " + key + " Internal Value : " + internalValue);
			if(null != internalValue
				&& !Constants.SPACE.equals(internalValue) && (internalValue.trim().length() != 0)) {
				isoMsg.set(key,internalValue);
				mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_DEBUG, fieldList.get(key) + " : " + internalValue);
			}
		}
		if(SysCode.MTI_0100.equals(mti)) {
            // [emaranan:02/25/2011] [Redmine#2210] [ACQ] STAN value in MTI 0400 Processing
            isoMsg.set(11,internalMsg.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue());

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
					"[BanknetInternalFormatProcess] : Field : 35 Internal Value : " + isoMsg.getString(35));

			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Setting field 61");

			ISOMsg fld61 = new ISOMsg(61); 
			fld61.set(1, "0");
			fld61.set(2, "0");
			fld61.set(3, "0");
			fld61.set(4, "0");
			fld61.set(5, "0");
			fld61.set(6, "0");
			fld61.set(7, "0");
			fld61.set(8, "0");
			fld61.set(9, "0");
			fld61.set(10, "0");
			
			// [lsosuan: 01/25/2011] [Redmine #2176] [Banknet simulator declined Sale(with IC) transaction because of Filed 61]
			if(SysCode.IC_INFO_EXIST.equals(internalMsg.getValue(CONTROL_INFORMATION.IC_FLAG).getValue()))
				fld61.set(11, "8");
			else
				fld61.set(11, "7");
			
			fld61.set(12, "00");
			fld61.set(13, (String) mcpProcess.getAppParameter(Keys.XMLTags.AQ_INSTITUTION_COUNTRY_CODE));
			fld61.set(14, internalMsg.getValue(OPTIONAL_INFORMATION.POSTAL_CODE).getValue()); 
						
			isoMsg.set(fld61);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : " +
					"Field : 61 Internal Value : " + fld61.getValue().toString());

			isoMsg.unset(unsetVariableBusinessMsg);
            
            /*
             * Redmine Issue 959 - Track Data not parsed correctly
             * 35. Track 2 Data
             */
            strTrack2 = internalMsg.getValue(HOST_COMMON_DATA.TRACK_2_DATA).getValue();
            // [lsosuan: 01/25/2011] [Redmine #2011] [Exception occurs in Banknet MCP]
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Track 2 : " + strTrack2);
            if(null != strTrack2 && strTrack2.length() > 0) { 
                isoMsg.set(35,change2Binary(strTrack2, true));  
            }
        
        // [lquirona 03/28/2011 - update logic based on Redmine Issue #2302]
		} else if(SysCode.MTI_0400.equals(mti)){
			isoMsg.unset(unsetVariableBusinessMsg);
            String stan = Constants.NO_SPACE;
            String stanRandom = Constants.NO_SPACE;
            String sourceID = Constants.NO_SPACE;
            String acqMsgTypeId = Constants.NO_SPACE;
            
			stan = internalMsg.getValue(HOST_COMMON_DATA.SYSTEM_TRACE_AUDIT_NUMBER).getValue();
			sourceID = internalMsg.getValue(HOST_HEADER.SOURCE_ID).getValue();
			
			if(SysCode.NETWORK_EDC.equals(sourceID)){
    			acqMsgTypeId = internalMsg.getValue(EDC_SHOPPING.MESSAGE_TYPE_ID).getValue();
    		}else if(SysCode.NETWORK_PGW.equals(sourceID)){
    			acqMsgTypeId = internalMsg.getValue(PGW.MESSAGE_TYPE_ID).getValue();
    		}// End of getting MTI of Acquiring Shopping Message
			
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : stan from HOST_COMMON_DATA: " + stan);
            // [emaranan:02/25/2011] [Redmine#2210] [ACQ] STAN value in MTI 0400 Processing
            if (SysCode.MTI_0200.equals(acqMsgTypeId)
                    && Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1.equals(transCode.substring(0, 2))) {
                isoMsg.set(11,stan);
            // [lquirona 03/28/2011 - added additional condition for PGW
            } else if ((SysCode.MTI_0400.equals(acqMsgTypeId)
                    && Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_REQ_1.equals(transCode.substring(0, 2))) 
                    || (((SysCode.MTI_0420.equals(acqMsgTypeId))
                    && Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE.equals(transCode.substring(0, 2))))
                    || (((SysCode.MTI_0421.equals(acqMsgTypeId))
                    && Constants.PREFIX_TRANS_CODE_AUTHORIZATION_REVERSAL_ADVICE.equals(transCode.substring(0, 2))))) {

		        	// [salvaro: 10/08/2011] For reversal, the STAN sent to Banknet should be new. But does not imply that STAN will be modified in internal format
		            stanRandom = ISOUtil.padleft((String)mcpProcess.getSequence(SysCode.STAN_SYS_TRACE_AUDIT_NUMBER).trim(), 6, '0');
		            isoMsg.set(11, stanRandom);
		            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : stan generated: " + stanRandom);

            }// End of else if 
            mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Field 11 " + isoMsg.getString(11));
		} // End of else if 0420 
		
		isoMsg.recalcBitMap();
		
		return isoMsg;
	}// End of businessMessage()

	/**
	 * Returns the PAN from Field 35 Track 2 Data or 45 Track 1 Data or 2 PAN
	 * @param originalMsg The received ISOMsg
	 * @return String The PAN value
	 * */
	private String getPAN(ISOMsg originalMsg) {
		String pan = "";
		char c;
		int i;
		String field45;
		String field35;

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getPAN() ");
		
		// Get PAN from Field 35 Track 2
		if(originalMsg.hasField(35)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
			"[BanknetInternalFormatProcess] : Get PAN from Field 35 Track 2");
			field35 = originalMsg.getString(35);
			i = 0;
			c = field35.charAt(i);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field35: " + field35);
			do {
				pan = pan + c;
				i++;					
				c = field35.charAt(i);
				
				/*
				 * [mqueja:06/08/2011][Redmine#2658]
				 * [Timeout when sending Banknet transactions with invalid track field separator]
				 */
				if(i > InternalFieldKey.HOST_COMMON_DATA.PAN.length()) {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : pan: " + pan + 
							" exceeds length of field");
					return " ";
				}
			} while(i < (field35.length() - 1) & c != 'D' & c != '=');
			// Get PAN from Field 45 Track 1
		} else if(originalMsg.hasField(45)){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG,
			"[BanknetInternalFormatProcess] : Get PAN from Field 45 Track 1");
			field45 = originalMsg.getString(45);
			i = 1;
			c = field45.charAt(i);
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field45: " + field45);
			do {
				pan = pan + c;
				i++;
				c = field45.charAt(i);
				
				/*
				 * [mqueja:06/08/2011][Redmine#2658]
				 * [Timeout when sending Banknet transactions with invalid track field separator]
				 */
				if(i > InternalFieldKey.HOST_COMMON_DATA.PAN.length()) {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : pan: " + pan + 
							" exceeds length of field");
					return " ";
				}
			} while( i < (field45.length() - 1) & c != '^'); 
			// Get PAN from Field 2	
		} else if(originalMsg.hasField(2)){
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : Get PAN from Field 2");
			pan = originalMsg.getString(2);			
		}// End of if-else

		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : pan " + pan);
		return pan;
	}// End of getPAN()

	/**
	 * Get Transaction Code for InternalFormat Message 
	 * according to MTI, Transaction Code, and other fields.   
	 * @param mti The Message Type Identifier
	 * @param field3Sub1 The transaction type
	 * @param field70 The network management information code
	 * @param originalMsg The received ISOMsg
	 * @return String The created transaction code
	 */
	private String getTransactionCode(String mti,String field3Sub1, String field70,ISOMsg originalMsg) {
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getTransactionCode ");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : mti: " + mti);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field 3.1: " + field3Sub1);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : field 70: "+ field70);

		String field49;
		/** 
		 * [START >> EOtayde Apr-12-2016: Bug# 838] 
		 * 		Change data type of Field 4 and Field 5 from Integer to Long as to be able to process
		 * 		transactions with amount greater than 2,147,483,647
		 */
//		int field4;
//		int field5;
		long field4;
		long field5;
		/** [END >> EOtayde Bug# 838] */ 
		// Start Redmine #4340 - Manual Cash transaction should be treated as purchase [MAguila: 20130528]
		String mcc = originalMsg.hasField(18) ? originalMsg.getString(18) : NO_SPACE;
		// End Redmine #4340 [MAguila: 20130528]

		/** 
		 * [START >> EOtayde Apr-12-2016: Bug# 838] 
		 * 		Change data type of Field 4 and Field 5 from Integer to Long as to be able to process
		 * 		transactions with amount greater than 2,147,483,647
		 */
//		field4 = (originalMsg.hasField(4)) 
//			? Integer.parseInt(originalMsg.getString(4)) 
//			: 0;
//		field5 = (originalMsg.hasField(5)) 
//			? Integer.parseInt(originalMsg.getString(5)) 
//			: 0;
		field4 = (originalMsg.hasField(4)) 
				? Long.parseLong(originalMsg.getString(4)) 
				: 0;
		field5 = (originalMsg.hasField(5)) 
				? Long.parseLong(originalMsg.getString(5)) 
				: 0;
		/** [END >> EOtayde Bug# 838] */ 	
		field49 = (originalMsg.hasField(49)) 
			? originalMsg.getString(49) 
			: "";
		
		// [eotayde 12232014] Logging purposes; aesthetics
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DE 4 : " + field4);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DE 5 : " + field5);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : DE 49 : " + field49);

		if(SysCode.MTI_0100.equals(mti)) {
			if((Constants.FLD3_1_TRANSACTION_TYPE_00).equals(field3Sub1)) {
				//[rrabaya:11/28/2014]RM#5850 add checking for purchaseASI for 0100 : Start
				if(isPurchaseASI){
					return Constants.TRANS_CODE_010406;
				//[rrabaya:11/28/2014]RM#5850 add checking for purchaseASI for 0100: End
				} else if(((Constants.ONEDOLLAR == field4) || Constants.ONEDOLLAR == field5) 
					&& Constants.FLD49_TRANSACTION_CURRENCY_CODE_840.equals(field49)){
					return Constants.TRANS_CODE_010103;
				} else {
					return Constants.TRANS_CODE_010101;
				}
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_01).equals(field3Sub1) 
					|| (Constants.FLD3_1_TRANSACTION_TYPE_17).equals(field3Sub1)
					|| (Constants.FLD3_1_TRANSACTION_TYPE_28).equals(field3Sub1)) {
				//[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASI for 0100 : Start
				if(isPaymentASI || isMoneySendPaymentASI){
					return Constants.TRANS_CODE_010407;
				//[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASI for 0100 : End
				// [start] [eotayde 12182014: RM 5838] For MoneySend Payment Authorization
				} else if (isMoneySendPaymentAuth) {
					return Constants.TRANS_CODE_010110;
				} 
				// [end] [eotayde 12182014: RM 5838] For MoneySend Payment Authorization
			
				if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
					return Constants.TRANS_CODE_010101;
				} else {
					return Constants.TRANS_CODE_010301;
				}
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_30).equals(field3Sub1)) {
				return Constants.TRANS_CODE_010403;
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_20).equals(field3Sub1)) {
				return Constants.TRANS_CODE_090101;
			}
		}
		if(SysCode.MTI_0120.equals(mti)) {
			if((Constants.FLD3_1_TRANSACTION_TYPE_00).equals(field3Sub1)) {
				//[rrabaya:11/28/2014]RM#5850 add checking for purchaseASI for 0120 : Start
				if(isPurchaseASI){
					return Constants.TRANS_CODE_020406;
					//[rrabaya:11/28/2014]RM#5850 add checking for purchaseASI for 0120 : End
				}else if(((Constants.ONEDOLLAR == field4) || Constants.ONEDOLLAR == field5) 
					&& Constants.FLD49_TRANSACTION_CURRENCY_CODE_840.equals(field49)){
					return Constants.TRANS_CODE_020103;
				} else {
					return Constants.TRANS_CODE_020101;
				}
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_01).equals(field3Sub1) 
					|| (Constants.FLD3_1_TRANSACTION_TYPE_17).equals(field3Sub1)) {
				if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
					return Constants.TRANS_CODE_020101;
				} else {
					return Constants.TRANS_CODE_020301;
				}
			//[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASI for 0120 : Start
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_28).equals(field3Sub1)){
				if(isPaymentASI || isMoneySendPaymentASI){
					return Constants.TRANS_CODE_020407;
			//[rrabaya:11/28/2014]RM#5850 add checking for PaymentASI and MoneySendPaymentASi for 0120 : End
			// [START] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Auth (0120)
				} else if (isMoneySendPaymentAuth) {
					return Constants.TRANS_CODE_020110;
				}
			// [END] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Auth (0120)
					
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_30).equals(field3Sub1)) {
				return Constants.TRANS_CODE_020403;
			}
		}
		if(SysCode.MTI_0190.equals(mti)) {
			if(originalMsg.hasField(127)) {
				return Constants.TRANS_CODE_041001;
			} else {
				return Constants.TRANS_CODE_041002;
			}
		}
		if(SysCode.MTI_0400.equals(mti)) {
			if((Constants.FLD3_1_TRANSACTION_TYPE_00).equals(field3Sub1)) {
				return Constants.TRANS_CODE_030101;
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_01).equals(field3Sub1) 
					|| (Constants.FLD3_1_TRANSACTION_TYPE_17).equals(field3Sub1)) {
				if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
					return Constants.TRANS_CODE_030101;
				} else {
					return Constants.TRANS_CODE_030301;
				}
			// [START] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Rev (0400)
			} else if ((Constants.FLD3_1_TRANSACTION_TYPE_28).equals(field3Sub1)) {
				// check if moneysend
				if (isMoneySendPaymentAuth) {
					return Constants.TRANS_CODE_030110;
				}
				return Constants.TRANS_CODE_030403;
			// [END] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Rev (0400)
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_30).equals(field3Sub1)) {
				return Constants.TRANS_CODE_030403;
			}
		}
		if(SysCode.MTI_0420.equals(mti)) {
			if((Constants.FLD3_1_TRANSACTION_TYPE_00).equals(field3Sub1)) {
				if(((Constants.ONEDOLLAR == field4) || Constants.ONEDOLLAR == field5) 
					&& Constants.FLD49_TRANSACTION_CURRENCY_CODE_840.equals(field49)){
					return Constants.TRANS_CODE_040103;
				} else {
					return Constants.TRANS_CODE_040101;
				}
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_01).equals(field3Sub1) 
					|| (Constants.FLD3_1_TRANSACTION_TYPE_17).equals(field3Sub1)) {
				if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
					return Constants.TRANS_CODE_040101;
				} else {
					return Constants.TRANS_CODE_040301;
				}
			// [start] [eotayde 01072015: RM 5838] Add checking for MoneySend Payment Reversal Advice
			} else if ((Constants.FLD3_1_TRANSACTION_TYPE_28).equals(field3Sub1)) {
					return Constants.TRANS_CODE_040110;
			// [end] [eotayde 01072015: RM 5838]
			} else if((Constants.FLD3_1_TRANSACTION_TYPE_30).equals(field3Sub1)) {
				return Constants.TRANS_CODE_040403;
			}
		}
		if(SysCode.MTI_0620.equals(mti)) {
			return Constants.TRANS_CODE_070801;
		}
		if(SysCode.MTI_0800.equals(mti)) {
			if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_001.equals(field70) 
					|| Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_061.equals(field70)) {
				return Constants.TRANS_CODE_000001;
			} else if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_002.equals(field70) 
					|| Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_062.equals(field70)) {
				return Constants.TRANS_CODE_000002;
			} else if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_060.equals(field70)) {
				return Constants.TRANS_CODE_000007;
			} else if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_161.equals(field70)) {
				return Constants.TRANS_CODE_000005;
			} else if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_270.equals(field70)) {
				return Constants.TRANS_CODE_000009;
			}
		}
		if(SysCode.MTI_0820.equals(mti)) {
			if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_161.equals(field70)) {
				return Constants.TRANS_CODE_000006;
			} else if(Constants.FLD70_NETWORK_MANAGEMENT_INFORMATION_CODE_363.equals(field70)) {
				return Constants.TRANS_CODE_000008;
			}
		}
		return "";
	}// End of getTransactionCode()

	/**
	 * Encode Field35 value to String of Binary(subtract 0x30).
	 * Encode String of Binary to Original Field 35(add 0x30).
	 * @param value The string value to be encoded
	 * @param toAdd True if will add 0x30, otherwise False
	 * @return String The string of binary
	 */
	private String change2Binary(String value, boolean toAdd) {
		
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : change2Binary()");
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : value: " + value);
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : toAdd: " + toAdd);

		char[] result;
		
		// Create a char array to convert
		result = new char[value.length()];

		// Get characters from value
		value.getChars(0, value.length(), result, 0);

		// Subtract 0x30 from original value
		for (int j = 0; j < result.length; j++) {
			if(toAdd) {
				result[j] = (char) (result[j] + Constant.HEX_30);
			} else {
				result[j] = (char) (result[j] - Constant.HEX_30);
			}
		}

		// Return new String.
		return new String(result);
	}// End of change2Binary()

	/**
	 * Returns the value in Field48
	 * @param origMsg The received ISOMsg
	 * @return String Value of Field48
	 * */
	private String getField48(ISOMsg origMsg) {
		String result = "";
		String value;
		String length;
		ISOMsg fld48;
		Hashtable<Integer, Object> fld48Children;
		Set c;
		
		try{
			if(origMsg.hasField(48)) {
				fld48 = (ISOMsg) origMsg.getValue(48);
				fld48Children = fld48.getChildren();
				c = fld48Children.keySet();
				for (Object key : c) {
					value = ((ISOField)fld48Children.get(key)).getValue().toString();
					length = String.valueOf(value.length());
					while(length.length() < 2) {
						length = "0" + length;
					}
					if(!String.valueOf(key).equals("0")) {
						result = key +  length +  value + result;
					} else {
						result = value + result;
					}
				}
				return result;
			}// End of if hasF48
		}catch (ISOException e) {
			return Constants.PAD_000 + Constants.SPACE;
		}catch (ClassCastException e) {
			return Constants.PAD_000 + Constants.SPACE;
		}catch (Exception e) {
			return Constants.PAD_000 + Constants.SPACE;
		}// End of try-catch
		return Constants.PAD_000 + Constants.SPACE;
	}// End of getField48()

	/**
	 * Returns the transaction code
	 * @param msg The received ISOMsg
	 * @return String The created transaction type
	 * */
	private String getTransactionType(ISOMsg msg) {
		/* 
		 * [03/11/2011] [Redmine #1689, #2184] Setting of transaction type
		 */
		String transType = null;
		String mti = null;
		String field3_1 = null;
		String mcc = msg.hasField(18) ? msg.getString(18) : NO_SPACE;
		
		try {
			mti = msg.getMTI();
		} catch (ISOException e) {
            String sysMsgId = mcpProcess.writeSysLog(LogOutputUtility.LOG_LEVEL_ERROR, 
                    Constants.CLIENT_SYSLOG_SCREEN_7001);
            mcpProcess.writeAppLog(sysMsgId, Level.ERROR, Constants.CLIENT_SYSLOG_SCREEN_7001, null,null, e.getCause());
			mcpProcess.writeAppLog(Level.ERROR, FepUtilities.getCustomStackTrace(e));			
		}
		field3_1 = (msg.getString(3)).substring(0, 2);
		
		if (SysCode.MTI_0100.equals(mti)) {
            if (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3_1)) {
            	//[rrabaya:11/28/2014]RM#5850 Setting transaction type for ASI transaction : Start
            	if(isPurchaseASI || isMoneySendPaymentASI){
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_43;
            	} 
            	//[rrabaya:11/28/2014]RM#5850 Setting transaction type for ASI transaction	: End
            	else{
                transType = Constants.FLD3_1_TRANSACTION_TYPE_21;
            	}
            } else if (Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3_1)
					|| Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3_1)){
					//[rrabaya:11/28/2014]RM#5850 Removing [F3.1 = 28]
					//|| Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3_1)) 
            	if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_21;
				} else {
					transType = Constants.FLD3_1_TRANSACTION_TYPE_42;
				}
            //[rrabaya:11/28/2014]RM#5850 Setting transaction type for ASI transaction : Start
            //if [F3.1 = 28]
            } else if(Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3_1)){
            	if(isPaymentASI || isMoneySendPaymentASI){
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_43;
            //[rrabaya:11/28/2014]RM#5850 Setting transaction type for ASI transaction : End
            	// [START] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Auth (0400)
            	} else if (isMoneySendPaymentAuth) {
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_25;
            	}
            	// [END] [eotayde 12182014: RM 5838] Add checking for MoneySend Payment Auth (0400)
			} else if  (Constants.FLD3_1_TRANSACTION_TYPE_30.equals(field3_1)) {
				transType = Constants.FLD3_1_TRANSACTION_TYPE_41;
			}
		} else if (SysCode.MTI_0120.equals(mti)) {
            if (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3_1)) {
            	//[rrabaya:11/28/2014]RM#5850 add checking PurcharseASI transaction : Start
            	if(isPurchaseASI){
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_43;
            	} else {
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_21;
            	}
            	//[[rrabaya:11/28/2014]RM#5850 add checking PurchaseASI transaction : End
            }
            // [Lquirona:20110809] [Redmine Issue #3175 - Type 42 for 0120 and F3.1=01]
            else if(Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3_1)
            		|| Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3_1)){ //ccalanog added 17 and 28
            	if (FLD18_MERCHANT_TYPE_6010.equals(mcc)) {
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_21;
				} else {
					transType = Constants.FLD3_1_TRANSACTION_TYPE_42;
				}
            }
            else if(Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3_1)){
            	if(isPaymentASI || isMoneySendPaymentASI){
            		transType = Constants.FLD3_1_TRANSACTION_TYPE_43;
            	// [start] [eotayde 01072015: RM 5838] Added transaction type for auth advice
            	} else if (isMoneySendPaymentAuth) {
					transType = Constants.FLD3_1_TRANSACTION_TYPE_25;
				} // [end] [eotayde 01072015]
            }
        } else if (SysCode.MTI_0400.equals(mti)
				&& (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3_1)
                        || Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3_1)
						|| Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3_1)
						|| Constants.FLD3_1_TRANSACTION_TYPE_30.equals(field3_1)
						|| Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3_1))) {
			transType = Constants.FLD3_1_TRANSACTION_TYPE_44;
		} else if (SysCode.MTI_0420.equals(mti)
				&& (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3_1)
                        || Constants.FLD3_1_TRANSACTION_TYPE_01.equals(field3_1)
						|| Constants.FLD3_1_TRANSACTION_TYPE_17.equals(field3_1)
						|| Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3_1) // [eotayde 12182014: RM 5838] Added for MoneySend Reversal Auth
						|| Constants.FLD3_1_TRANSACTION_TYPE_30.equals(field3_1))) {
			transType = Constants.FLD3_1_TRANSACTION_TYPE_44;
		} 
		mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getTransactionType(): " + transType);
		return transType;
	}// End of getTransactionType
	
	/**
	 * Returns the Expiration Date
	 * @param isoMsg The received ISOMsg
	 * @return String The expiration Date from either F35/F45/F14
	 * */
	private String getExpirationDate(ISOMsg isoMsg) {
		// csawi20111005 : updated the implementations
		String[] track;
		if(isoMsg.hasField(35)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getExpirationDate() from DE 35");
			track = isoMsg.getString(35).split("=");
			if(track.length > 1) {
				return track[1].substring(0, 4);
			} else {
				track = isoMsg.getString(35).split("D");
				if(track.length > 1) {
					return track[1].substring(0, 4);
				} else {
					mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getExpirationDate() :" + track);
					return "";
				}
			}
		} else if(isoMsg.hasField(45)) {
			mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getExpirationDate() from DE 45");
			track = isoMsg.getString(45).split("\\^");
			if(track.length > 1) {
				return track[2].substring(0, 4);
			} else {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getExpirationDate() :" + track);
				return "";
			}
		} else {
			if(isoMsg.hasField(14)) {
				mcpProcess.writeAppLog(LogOutputUtility.LOG_LEVEL_DEBUG, "[BanknetInternalFormatProcess] : getExpirationDate() from DE 14");
				return isoMsg.getString(14);
			}
		}// End of if-else
		
		return "";
	}// End of getExpirationDate()
	
	/**
	 * Star RM5850 Reminder for MasterCard April 2014 Compliance
	 * Global 555 Payment Account Status Inquiry Transactions (2013 Enhancement)
	 * This  method validates an account status inquiry transaction
	 * @param isoMsg
	 * @throws ISOException
	 */
	private void checkASI(ISOMsg isoMsg) throws ISOException {
		// Check field if existing before getting the value
		String field3Sub1 = (isoMsg.hasField(3) ? isoMsg.getString(3)
				.substring(0, 2) : Constants.NO_SPACE);

		String mcc = (isoMsg.hasField(18) ? mcc = isoMsg.getString(18)
				: Constants.NO_SPACE);

		if (Constants.FLD3_1_TRANSACTION_TYPE_00.equals(field3Sub1) && isASI) {
			isPurchaseASI = true;
		}

		if (Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3Sub1) && isASI) {
			// Check MCC for MoneySend Payment ASI
			if (Constants.MONEYSENDPAYMENT_MCC_LIST.contains(mcc)) {
				isMoneySendPaymentASI = true;
			} else {
				isPaymentASI = true;
			}
		}
		return;
	}
	
	/**
	 * Start RM5850 http://fep-redmine/redmine/issues/5245
	 * This method checks if transactions is ASI.
	 * @param isoMsg
	 * @return boolean 
	 * 		true : is an account status inquiry transaction
	 * 		false : is not an account status inquiry transaction
	 * @throws ISOException 
	 */
	private boolean isASI(ISOMsg isoMsg) throws ISOException {
		isASI = false;
		isPurchaseASI = false;
		isPaymentASI = false;
		isMoneySendPaymentASI = false;

		// DE61
		ISOMsg field61_posData = (isoMsg.hasField(61) ? (ISOMsg) isoMsg
				.getValue(61) : null);

		// DE61_7
		String field61Sub7 = (isoMsg.hasField(61)) ? (field61_posData)
				.hasField(7) ? (field61_posData).getString(7) : NO_SPACE
				: NO_SPACE;

		// DE61_7 Check
		if (Constants.FLD61_7_POS_TRANSACTION_STATUS_8.equals(field61Sub7)) {
			isASI = true;
		}
		return isASI;
	}
	
	/**
	 * [eotayde 12182014: RM 5838: [Mastercard Enhancement] - MoneySend Enhancements
	 * 		- Implemented in ACSM and replicated in ACSA]
	 * 
	 * Validate transaction if it is an MoneySend Payment Authorization
	 * @param originalISOMsg
	 * @return boolean 
	 * 		true : is a moneysend payment transaction
	 * 		false : is not a moneysend payment transaction
	 * @throws ISOException
	 */
	private boolean isMoneySendPaymentAuth(ISOMsg originalISOMsg)
			throws ISOException {

		String field3Sub1 = (originalISOMsg.hasField(3) ? originalISOMsg
				.getString(3).substring(0, 2) : Constants.NO_SPACE);

		String mcc = (originalISOMsg.hasField(18) ? mcc = originalISOMsg
				.getString(18) : Constants.NO_SPACE);

		if (Constants.FLD3_1_TRANSACTION_TYPE_28.equals(field3Sub1) && !isASI) {
			if (Constants.MONEYSENDPAYMENT_MCC_LIST.contains(mcc)) {
				return true;
			}
		}
		return false;
	}
	
}// End of class