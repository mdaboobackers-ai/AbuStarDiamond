function removeAllRowsFromTable() {
    //alert("11");
    $("#gvData").empty();
}
function removeAllRowsFromTable_top3() {
    //alert("11");
    $("#gvData_top3").empty();
}
function gvData_Trending() {
    $("#gvData_Trending").empty();

}
function removeAllRowsFromTableGCSC() {
    //alert("11");
    $("#gvDataGCSC").empty();
}
function removeAllRowsFromTableGCSC_Silver() {
    //alert("11");
    $("#gvData_SilverRates_GCSC").empty();
}
function gvData_Trending() {
    $("#gvData_Trending").empty();

}
//Silver Rates
function removeAllRowsFromTable_gvData_SilverRates() {
    $("#gvData_SilverRates").empty();
}
function gvData_Trending_gvData_Trending_SilverRates() {
    $("#gvData_Trending_SilverRates").empty();

}
function gvData_Gold_Silver_INR_coinss() {
    $("#gvData_Gold_Silver_INR_coinss").empty();
}

function removeAllRowsFromTable_gvData_Trending_GoldRates() {
    $("#gvData_Trending_GoldCoins").empty();
}

function removeAllRowsFromTable_gvData_GoldRates() {
    $("#gvData_GoldCoins").empty();
}
function callBuySell(scripCode, scripName) {
	// alert(scripCode);
	var UserID_M = localStorage.getItem("username");
    var Password_M = localStorage.getItem("password");


    if (!UserID_M && !Password_M) 
	{
        window.location.href = "www/Login.htm";
    }
    else 
	{
        //startSpinner();
	   sessionStorage.scripname = scripName;
       sessionStorage.scripcode = scripCode;
	   window.location.href = "www/BuySell.htm";
	}
}

$(document).ready(function(){
	var UserID_M = localStorage.getItem("username");
    var Password_M = localStorage.getItem("password");


    if (!UserID_M && !Password_M) 
	{
		$("#tradeLink").hide();
		$("#pendingOrdersLink").hide();
    }
    else 
	{
        $("#tradeLink").show();
		$("#pendingOrdersLink").show();
	}
});

function fnStartClock() {

    try {
        //CallWebServiceFromJqueryLiveRateMessage();
        //alert("fnStartClock");
        refreshData();
        oInterval = setInterval("refreshData()", 10000);
        CallWebServiceFromJqueryMarquee();
        var timerMarquee = setInterval("CallWebServiceFromJqueryMarquee()", 30000);
    }
    catch (e) {
        // alert("fnStartClock" + e);
    }
}


function fnStartClock_0() {

    try {
        //alert("0");
        //		startSpinner();
        CallWebServiceFromJqueryLiveRateMessage();

        CallWebServiceFromJquery();
        oInterval_0 = setInterval("CallWebServiceFromJquery()", 500); //500  
        setInterval("resetLiveRateTable()", 10000); //500  
        //float_Message();
    }
    catch (e) {
        // alert("fnStartClock" + e);
    }
}

function fnStartClock_1() {

    try {
        //startSpinner();
        CallWebServiceFromJqueryLiveRateMessage();
        //startSpinner();
        //alert("fnStartClock");       
        //        oInterval = setInterval("refreshData()", 500); //500
        //        oInterval = setInterval("refreshData()", 500); //500

        CallWebServiceFromJqueryGoldCoins();
        oInterval_1 = setInterval("CallWebServiceFromJqueryGoldCoins()", 500); //500
        //float_Message();
    }
    catch (e) {
        // alert("fnStartClock" + e);
    }
}





function fnStartClock_2() {

    try {
        //alert("0");
        //		startSpinner();
        CallWebServiceFromJqueryLiveRateMessage();

        CallWebServiceFromJquerySilverCoins();
        oInterval_0 = setInterval("CallWebServiceFromJquerySilverCoins()", 500); //500  
        setInterval("resetLiveRateTable_Silver()", 10000); //500  
        //float_Message();
    }
    catch (e) {
        // alert("fnStartClock" + e);
    }
}



function resetLiveRateTable() {
    showOnce = "0";
}

function resetLiveRateTable_Silver() {
    showOnce_silver = "0";
}

function resetLiveRateTable_coins() {
    showOnce_coins = "0";
}





function refreshData() {
    //alert("refreshData");
    CallWebServiceFromJquery();

   // CallWebServiceFromJqueryGoldCoins();

    //CallWebServiceFromJquerySilverCoins();
}
function fnStopClock_0() {
    try {
        clearInterval(oInterval_0);
    }
    catch (e) {
        //alert("fnStopClock" + e);
    }
}

function fnStopClock_1() {
    try {
        clearInterval(oInterval_1);
    }
    catch (e) {
        //  alert("fnStopClock" + e);
    }
}


function fnStopClock_2() {
    try {
        clearInterval(oInterval_2);
    }
    catch (e) {
        //  alert("fnStopClock" + e);
    }
}




function addZero(i) {
    if (i < 10) {
        i = "0" + i;
    }
    return i;
}

function updateTime() {
    var d = new Date();
    var x = document.getElementById("cur_time");
    var h = addZero(d.getHours());
    var m = addZero(d.getMinutes());
    var s = addZero(d.getSeconds());
    var ampm = h >= 12 ? 'pm' : 'am';
    h = h % 12;
    h = h ? h : 12; // the hour '0' should be '12'
    x.innerHTML = h + ":" + m + ":" + s;
}
var maxRows = 0;
var oldData;
var oldData01;
var oldData02;
var oldData03;
var screenFontSize = 14;
var oldDataTop;
var oldDataGoldCoins;
var oldDataSilverCoins;
var counterRefresh = 0;
var showOnce = "0";
var showOnce_silver = "0";
var showOnce_coins = "0";
var oldData_Gold_silver_INR_coins;
var oldDataMCX;
var SwiperHeading;
var oldDataTrending_SilverRates;
//Spotttttttttttttttttttt
function CallWebServiceFromJquery() {
    try {


        var template = localStorage.defaultScripTemplateId;

        if (TemplateID) {
            template = TemplateID;
        }
// alert(template);

        // alert("http://bulliontradingbcast.chirayusoft.com:7767/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/" + template);
        $.ajax({
            type: "GET",
            url: "https://" + localStorage.ipAddressBCast + ":" + localStorage.step3StreamingPort + "/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/" + template,
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccess,
            error: OnError,
            cache: false
        });
    }
    catch (e) {
        //alert("CallWebServiceFromJquery " + e);
    }

}

var myColor_Background = "#0d1539";
var Color_ForeColor = "#000";
var Color_ScriptColor = "#141e46";

var Script_Font_LiveRatesCoins = "32px";
var Change_ScriptNameFont = "13px";


//Spottttttttttttttttttt
function OnSuccess(data, status) {
    //alert(data);
    try {
        //updateTime();
        //stopSpinner();


        var messagesDesktopp = "";
        messagesDesktopp = data.split("\n");
        //alert(messagesDesktopp.length);
        if (typeof oldData != 'undefined') {

        }
        else {
            //alert("1");
            oldData = data.toString();
        }
        var messagesOldDesktop = oldData.split("\n");

        if (typeof messagesDesktopp != 'undefined') {
            if (maxRows == 0) {
                maxRows = messagesDesktopp.length;
            }

            removeAllRowsFromTable();
            var zebra = "";
            zebra = document.getElementById("gvData"); //Desktopppppppppppppppppppppppppppp
            var trow = "";
            //GOLD
            var retDesktop = "";
            retDesktop = messagesDesktopp[0].split("\t");
            //alert(retDesktop.length);
            var oldRetDesktop = "";
            var trowString = "";
            oldRetDesktop = messagesOldDesktop[0].split("\t");

            if (typeof retDesktop[2] != 'undefined') {

                trowString = trowString + "<table class=\"table1001\" style=\"\"><tr><td align=\"center\" style=\"width: 25%;\">";

                if (retDesktop[3] > oldRetDesktop[3]) {
                    trowString = trowString + "<table  width=\"100%\" class=\"goldd\" style=\"\"><tr style=\"\"><td class=\"sell\" style=\"color:#000;text-align:center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:#00a650;\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                                                "</table>";
                }
                else if (retDesktop[3] < oldRetDesktop[3]) {
                    trowString = trowString + "<table  width=\"100%\" class=\"goldd\" style=\"\"><tr style=\"\"><td class=\"sell\" style=\"color:#000;text-align:center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:red;\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                                                "</table>";
                }
                else {
                    trowString = trowString + "<table  width=\"100%\" class=\"goldd\" style=\"\"><tr style=\"\"><td class=\"sell\" style=\"color:#000;text-align:center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:black;\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                                              "</table>";
                }

                trowString = trowString + "</td>";

                //}
            }
            //SILVER
            retDesktop = messagesDesktopp[1].split("\t");
            oldRetDesktop = messagesOldDesktop[1].split("\t");
            if (typeof retDesktop[2] != 'undefined') {

                if (retDesktop[3] > oldRetDesktop[3]) {

                    trowString = trowString + "<td align=\"center\" style=\"width: 25%;\"><table  width=\"100%\" class=\"goldd\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:#00a650;\">" + retDesktop[3] + "</span></td></tr>" +
                        "<tr>" +
                            "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                        "</tr>" +
                    "</table></td>";

                }
                else if (retDesktop[3] < oldRetDesktop[3]) {
                    trowString = trowString + "<td align=\"center\" style=\"width: 25%;\"><table  width=\"100%\" class=\"goldd\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:red;\">" + retDesktop[3] + "</span></td></tr>" +
                            "<tr>" +
                                "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                            "</tr>" +
                            "</table></td>";
                }
                else {
                    trowString = trowString + "<td align=\"center\" style=\"width: 25%;\"><table  width=\"100%\" class=\"goldd\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:black;\">" + retDesktop[3] + "</span></td></tr>" +
                        "<tr>" +
                            "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                        "</tr>" +
                    "</table></td>";
                }

            }
            //INR
            retDesktop = messagesDesktopp[2].split("\t");
            oldRetDesktop = messagesOldDesktop[2].split("\t");
            if (typeof retDesktop[2] != 'undefined') {
                var trowString;
                //if (deletedScrips[2] != "0") {
                if (retDesktop[3] > oldRetDesktop[3]) {


                    trowString = trowString + "<td style=\"width:25%;\" align=\"center\"><table class=\"goldd\" width=\"100%\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:#00a650;\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                    "</table></td>"

                }
                else if (retDesktop[3] < oldRetDesktop[3]) {

                    trowString = trowString + "<td style=\"width:25%;\" align=\"center\"><table class=\"goldd\" width=\"100%\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:red;\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                    "</table></td>";
                }
                else {

                    trowString = trowString + "<td style=\"width:25%;\" align=\"center\"><table class=\"goldd\" width=\"100%\" style=\"\"><tr><td style=\"color:#000;text-align: center !Important;font-size: 16px;font-weight:700;\">" + retDesktop[2] + "</td></tr><tr><td id=\"" + retDesktop[1] + "BUY\" style=\"color:#000;text-align: center !Important;font-size: 16px;\"><span class=\"top5span\" style=\"color:black\">" + retDesktop[3] + "</span></td></tr>" +
                                                "<tr>" +
                                                    "<td style=\"color: #000;text-align: center !Important;\"><span class=\"bloc_GS\" style=\"color:red;\">" + retDesktop[6] + "</span> | <span class=\"bloc_GS\" style=\"color:#00a650;\">" + retDesktop[5] + "</span></td>" +
                                                "</tr>" +
                    "</table></td>";
                }


                //}
            }

            trowString = trowString + "</tr></table>";







                        trowString = trowString + "<table class=\"tt_33\" width=\"100%\" style=\"margin-top:0%;\"> " +
                                                        " <tr > " +
                                                            " <td style=\"padding: 0px 10px 0;\"> " +
                                                            //    "<table  width=\"100%\" style=\"background:black;margin-bottom:1%;\"> " +
                                                             //   "<tr>" +
                                                             //       "<td width=\"59%\" style=\"font-size: 16px;color:#000;font-weight:BOLD;padding: 10px 0px 10px 10px;text-align:left; \">" +
                        												//"<span>PRODUCT</span>" +
                                                              //      "</td>" +
                                                                   

                                                            //        "<td width=\"45%\" style=\"font-size: 16px;padding:5px 5px ;color:#000;font-weight:BOLD;text-align:center; \" >" +
                                                                      //  "<span>PRICE</span>" +
                                                             //       "</td>" +

                                                                    // "<td width=\"25%\" style=\"font-size: 16px;padding:5px 3px;color:#000;font-weight:BOLD;text-align:center; \" >" +
                                                                        // "<span>HIGH/LOW</span>" +
                                                                    // "</td>" +

                        //                                                                "<td style=\"width:20%; text-align: center !Important\" >" +
                        //                                                                    "<span></span>" +
                        //                                                                "</td>" +

                        //"<td style=\"width:15%; text-align: center !Important\" >" +
                        //   "<span>LOW</span>" +
                        //"</td>" +
                                                            //        "</tr>" +
                                                            //    "</table>"
                        "</td>" +
                                                            "</tr>" +
                        //Second Row
                                                                 " <tr> " +
                                                              " <td> ";
            //messages.length
            //messages.length
    for (var i = 5; i < messagesDesktopp.length; i++) {
        //var ret = jQuery.parseJSON(messages[i]);
        var ret = messagesDesktopp[i].split("\t");
        var oldRet;


        oldRet = messagesOldDesktop[i].split("\t");
        if (typeof ret[1] != 'undefined') {

            if (ret[3] > oldRet[3]) {

                trowString = trowString +
                //"<table width=\"100%\"><tr><td onclick=\"callBuySell('" + ret[1] + "')\" >" +
                                    "<table class=\"res_mob_font_width\"  width=\"100%\" style=\"border-bottom: 0px solid #000;\"> " +
                                        "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\" style=\"text-align: center;\"> " +
                                            "<td class=\"buy_sell_label\" style=\"width:50%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td> " ;
                                           // "<td style=\"width:33%;text-align: center !Important;padding-top: ;background:#00D600;\">" +
                                           // "<span style=\"font-size: 40px; color:#fff;font-weight:700\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                            //"</td>";

            }
            else if (ret[3] < oldRet[3]) {

                trowString = trowString +
                //                                "<table width=\"100%\">"+
                //                                    "<tr>"+
                //                                        "<td>"+
                                    "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 0px solid #000;\">" +
                                        "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\"  style=\"text-align: center;\">" +
                                            "<td class=\"buy_sell_label\" style=\"width:50%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td>" ;
                                            //"<td style=\"width:33%;text-align: center !Important;padding-top: ;background:red;\">" +
                                           // "<span style=\" font-size: 40px; color:#fff;font-weight:700\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                           // "</td>";

            }
            else {
                trowString = trowString +
                //                                    "<table width=\"100%\">"+
                //                                        "<tr>"+
                //                                            "<td>"+
                                        "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 0px solid #000;\">" +
                                            "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\"  style=\"text-align: center;\">" +
                                                "<td class=\"buy_sell_label\" style=\"width:50%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td>" ;
                                               // "<td style=\"width:33%;text-align: center !Important;padding-top: ;\">" +
                                               // "<span style=\" font-size: 40px;color:#000;font-weight:700;\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                                //"</td>";

            }





            //For Sell

            if (ret[4] > oldRet[4]) {

                trowString = trowString +
                                "<td style=\"width:33%;text-align: center !Important;padding-top: ;background:#00D600;\">" +
                                "<span style=\"font-size: 32px;color:#fff;font-weight:700;\">" + ret[4] + "</span>" + //<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                "</td>";
            }
            else if (ret[4] < oldRet[4]) {

                trowString = trowString +

                                            "<td style=\"width:33%;text-align: center !Important;padding-top: ;background:red;\">" +
                //"<span style=\"font-size: 17px;background-color:#d0161e;border-radius:10px;color:#FFF;font-weight:700\">" + sellSmall + "</span>
                                            "<span style=\"font-size: 32px;color:#fff;font-weight:700\">" + ret[4] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                            "</td>";

            }
            else {
                trowString = trowString +

                                                "<td style=\"width:33%;text-align: center !Important;padding-top: ;\">" +
                //<span style=\"font-size: 17px; padding:1px 5px;padding: 3px;font-weight:700;color:black\">" + sellSmall + "</span>
                                                "<span style=\"font-size: 32px; padding:1px 5px;font-weight:700;color:#000\">" + ret[4] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                                "</td>";
            }


            //trowString = trowString + "</td></tr></table>";
            // trowString = trowString + "<td style=\"width: 25%;\"><span style=\"text-align: center !Important;font-size: 16px !important;color:#000;\">" + ret[5] + "</span> / <span style=\"text-align: center !Important;font-size: 16px !important;color:#000;\">" + ret[6] + "</span></td>";
            trowString = trowString + "</tr></table>";

            //}

        }
        oldData = data.toString();

    }
    trowString = trowString + "</td></tr></table>"; //</td></tr>







    //EXPECTED MCX #################################
    //EXPECTED MCX #################################
    //EXPECTED MCX #################################

    trowString = trowString + "<table class=\"tt_33\" width=\"100%\" style=\"margin-top:1%;\"> " +
                                                 " <tr > " +
                                                     " <td style=\"padding: 0px 0px 0;\"> " +
                                                         "<table  width=\"100%\" style=\"background: #FFF;margin-bottom: 5px;\"> " +
                                                         "<tr>" +
                                                             "<td width=\"35%\" style=\"font-size: 16px;color:#000;font-weight:Bold;padding: 5px 10px;text-align:left; \">" +
                                        "<span>COSTING</span>" +
                                                            "</td>" +
                                                                    "<td width=\"20%\" style=\"font-size: 16px;padding:5px 10px;color:#000;font-weight:700;\" >" +
//                                                                       "<span>BUY</span>" +
// //                                                                    "</td>" +
//
// //                                                                    "<td width=\"20%\" style=\"font-size: 16px;padding:5px 10px;color:#000;font-weight:700;\" >" +
// //                                                                        "<span>SELL</span>" +
// //                                                                    "</td>" +
//
// //                                                                    "<td width=\"25%\" style=\"font-size: 16px;padding:5px 10px;color:#000;font-weight:700;\" >" +
// //                                                                        "<span>HIGH/LOW</span>" +
// //                                                                    "</td>" +
//
//                 //"<td style=\"width:15%; text-align: center !Important\" >" +
//                 //   "<span>LOW</span>" +
//                 //"</td>" +
                                                             "</tr>" +
                                                        "</table>"
                 "</td>" +
                                                    "</tr>" +
    //Second Row
                                             " <tr> " +
                                          " <td style=\"padding:0 10px 0;\"> ";



    //messages.length
    for (var i = 3; i < 5; i++) {
        //var ret = jQuery.parseJSON(messages[i]);
        var ret = messagesDesktopp[i].split("\t");
        var oldRet;


        oldRet = messagesOldDesktop[i].split("\t");
        if (typeof ret[1] != 'undefined') {

             if (ret[3] > oldRet[3]) {

                trowString = trowString +
                //"<table width=\"100%\"><tr><td onclick=\"callBuySell('" + ret[1] + "')\" >" +
                                    "<table class=\"res_mob_font_width\"  width=\"100%\" style=\"border-bottom: 0px solid #000;\"> " +
                                        "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\" style=\"text-align: center;\"> " +
                                            "<td class=\"buy_sell_label\" style=\"width:35%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td> " +
                                            "<td style=\"width:20%;text-align: center !Important;padding-top: ;background:#00d600;\">" +
                                            "<span style=\"padding: 3px;font-size: 32px; color:#fff;font-weight:700\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                            "</td>";

            }
            else if (ret[3] < oldRet[3]) {

                trowString = trowString +
                //                                "<table width=\"100%\">"+
                //                                    "<tr>"+
                //                                        "<td>"+
                                    "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 0px solid #000;\">" +
                                        "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\"  style=\"text-align: center;\">" +
                                            "<td class=\"buy_sell_label\" style=\"width:35%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td>" +
                                            "<td style=\"width:20%;text-align: center !Important;padding-top: ;background:red;\">" +
                                            "<span style=\"padding: 3px; font-size: 32px; color:#fff;font-weight:700\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                            "</td>";

            }
            else {
                trowString = trowString +
                //                                    "<table width=\"100%\">"+
                //                                        "<tr>"+
                //                                            "<td>"+
                                        "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 0px solid #000;\">" +
                                            "<tr onclick=\"callBuySell('" + ret[1] + "','" + ret[2] + "');\"  style=\"text-align: center;\">" +
                                                "<td class=\"buy_sell_label\" style=\"width:35%;padding: 5px 0px 5px 10px;text-align:left;font-weight:700;color:#000\">" + ret[2] + "</td>" +
                                                "<td style=\"width:20%;text-align: center !Important;padding-top: ;\">" +
                                                "<span style=\"padding: 3px; font-size: 32px;color:#000;font-weight:700;\">" + ret[3] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">L : " + ret[6] + "</span>" +
                                                "</td>";

            }





            //For Sell

            if (ret[4] > oldRet[4]) {

                trowString = trowString +
                                "<td style=\"width:20%;text-align: center !Important;padding-top: ;background:#00d600;\">" +
                                "<span style=\"padding: 3px;font-size: 32px;color:#fff;font-weight:700;\">" + ret[4] + "</span>" + //<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                "</td>";
            }
            else if (ret[4] < oldRet[4]) {

                trowString = trowString +

                                            "<td style=\"width:20%;text-align: center !Important;padding-top: ;background:red;\">" +
                //"<span style=\"font-size: 17px;background-color:#d0161e;border-radius:10px;color:#FFF;font-weight:700\">" + sellSmall + "</span>
                                            "<span style=\"padding: 3px;font-size: 32px;color:#fff;font-weight:700;\">" + ret[4] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                            "</td>";

            }
            else {
                trowString = trowString +

                                                "<td style=\"width:20%;text-align: center !Important;padding-top: ;\">" +
                //<span style=\"font-size: 17px; padding:1px 5px;padding: 3px;font-weight:700;color:black\">" + sellSmall + "</span>
                                                "<span style=\"padding: 3px;font-size: 32px;font-weight:700;color:#000\">" + ret[4] + "</span>" +
                //"<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:#f7be08;font-weight:700;display: " + displayy + ";\">H : " + ret[5] + "</span>" +
                                                "</td>";
            }


            //trowString = trowString + "</td></tr></table>";
            trowString = trowString + "<td style=\"width: 25%;\"><span class=\"bloc_GS\" style=\"text-align: center !Important;font-size: 16px !important;color:#00a650;\">" + ret[5] + "</span> / <span class=\"bloc_GS\" style=\"text-align: center !Important;font-size: 16px !important;color:red;\">" + ret[6] + "</span></td>";
            trowString = trowString + "</tr></table>";

            //}

        }
        oldData = data.toString();

    }
    trowString = trowString + "</td></tr></table>"; //</td></tr>
































    trow = $(trowString);
    trow.prependTo(zebra);



    trow_top3 = $(trowString_top3);
    trow_top3.prependTo(zebra_top3);



}
        if (counterRefresh == 0) {
            //myScroll.refresh();
            counterRefresh = counterRefresh + 1;
        }
        oldData = data.toString();
        //OnSuccessMobileTop(data, status);

    }
    catch (e) {
        //alert("OnSuccess" + e);
        oldData = data.toString();
        //alert(oldData);
    }




}
function OnError(request, status, error) {
    //alert("Webservice Error: " + request.statusText + " " + error);
}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
function CallWebServiceFromJquerySilverCoins() {
    try {

        //urlLink = "http://mobiletradingbroadcast.arihantspot.com:7777/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/silver";

        //alert('CallWebServiceFromJquery');
        $.ajax({
            type: "GET",
              url: "https://" + localStorage.ipAddressBCast + ":" + localStorage.step3StreamingPort + "/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/",
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccess_SilverRates,
            error: OnError_SilverRates,
            cache: false
        });
    }
    catch (e) {
        //alert("CallWebServiceFromJquery " + e);
    }
}



function OnSuccess_SilverRates(data, status) {
    //alert(data);
    try {


        // if (showOnce_silver == "0") {
            // showOnce_silver = "1";
        // }
        // else {

            // updateOnlyData_Silver(data);
            // return;
        // }

        //stopSpinner();
        var messages = data.split("\n");


        if (typeof oldDataSilverCoins != 'undefined') {

        }
        else {
            //alert("1");
            oldDataSilverCoins = data.toString();
        }
        var messagesOld = oldDataSilverCoins.split("\n");

        if (typeof messages != 'undefined') {
            if (maxRows == 0) {
                maxRows = messages.length;
            }

            removeAllRowsFromTable_gvData_SilverRates();

            var zebra_SilverRates = document.getElementById("gvData_SilverRates");

            var trow_SilverRates;

            //GOLD
            var ret = messages[0].split("\t");
            var oldRet;
            var trowString = "";
            oldRet = messagesOld[0].split("\t");
            if (typeof ret[1] != 'undefined') {


                trowString = trowString + "<tr><td align=\"center\" style=\"width: 33%;\">";

                //alert(retDesktop[3] + '-->' + oldRetDesktop[3]);
                if (ret[3] > oldRet[3]) {
                    trowString = trowString + "<table width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr ><td class=\"sell\" style=\"color:#251f1f;text-align:center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:green;\">" + ret[3] + "</span></td></tr></table>"
                }
                else if (ret[3] < oldRet[3]) {
                    trowString = trowString + "<table width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr ><td class=\"sell\" style=\"color:#251f1f;text-align:center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:red;\">" + ret[3] + "</span></td></tr></table>"
                }
                else {
                    trowString = trowString + "<table width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr ><td class=\"sell\" style=\"color:#251f1f;text-align:center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:#251f1f;\">" + ret[3] + "</span></td></tr></table>"
                }

                trowString = trowString + "</td>";



            }
            //SILVER
            ret = messages[1].split("\t");
            oldRet = messagesOld[1].split("\t");
            if (typeof ret[1] != 'undefined') {


                if (ret[3] > oldRet[3]) {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:green;\">" + ret[3] + "</span></td></tr></table></td>";
                }
                else if (ret[3] < oldRet[3]) {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:red;\">" + ret[3] + "</span></td></tr></table></td>";
                }
                else {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:#251f1f;\">" + ret[3] + "</span></td></tr></table></td>";
                }


            }
            //INR
            ret = messages[2].split("\t");
            oldRet = messagesOld[2].split("\t");
            if (typeof ret[2] != 'undefined') {
                var trowString;

                if (ret[3] > oldRet[3]) {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:green;\">" + ret[3] + "</span></td></tr></table></td>";
                }
                else if (ret[3] < oldRet[3]) {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:red;\">" + ret[3] + "</span></td></tr></table></td>";
                }
                else {
                    trowString = trowString + "<td align=\"center\" style=\"width: 33%;\"><table  width=\"100%\" class=\"goldd\" style=\"border-radius: 5px;\"><tr><td style=\"color:#251f1f;text-align: center !Important;font-size: 100%;border-top-left-radius: 5px;border-top-right-radius: 5px;\">" + ret[2] + "</td></tr><tr><td id=\"" + ret[1] + "BUYSILVER\" style=\"color:#251f1f;text-align: center !Important;font-size: x-large;border-bottom-left-radius: 5px;border-bottom-right-radius: 5px;\"><span style=\"color:#251f1f;\">" + ret[3] + "</span></td></tr></table></td>";
                }



            }

           
           


            trowString = trowString + "</tr></table></td>";
            trowString = trowString + "</tr>";

            trowString = trowString;
            trow_SilverRates = $(trowString);
            trow_SilverRates.prependTo(zebra_SilverRates);
            oldDataSilverCoins = data.toString();

        }

        if (counterRefresh == 0) {
            //myScroll.refresh();
            counterRefresh = counterRefresh + 1;
        }


        Success2_Trending_SilverRates(data, status); //OnSuccess2 Function 2

    }
    catch (e) {
        //alert("OnSuccess" + e);
    }


}
function OnError_SilverRates(request, status, error) {
    //alert("Webservice Error: " + request.statusText + " " + error);
}





function Success2_Trending_SilverRates(data, status) {
    //alert(data);
    try {

        var messagesDesktopp = "";
        messagesDesktopp = data.split("\n");

        if (typeof oldDataTrending_SilverRates != 'undefined') {
        }
        else {
            oldDataTrending_SilverRates = data.toString();
        }
        var messagesOldDesktop = oldDataTrending_SilverRates.split("\n");

        if (typeof messagesDesktopp != 'undefined') {
            if (maxRows == 0) {
                maxRows = messagesDesktopp.length;
            }

            gvData_Trending_gvData_Trending_SilverRates();
            var zebra1_SilverRates = "";
            zebra1_SilverRates = document.getElementById("gvData_Trending_SilverRates"); //Trending Lower Portion
            var trow1_SilverRates = "";
            var trowString = "";

            var retDesktop = "";
			  var oldRetDesktop;
            retDesktop = messagesDesktopp[0].split("\t");

            if (typeof retDesktop[1] != 'undefined') {
                
                  trowString = trowString + "<table class=\"tt_33\" width=\"100%\" style=\"\"> " +
                                            " <tr > " +
                                                " <td style=\"\"> " +
                                                    "<table class=\"heading\" width=\"100%\" > " +
                                                    "<tr>" +
                                                        "<td class=\"heading1\" width=\"40%\" style=\"font-size: 15px;font-family: Georgia, serif;color:#C7272B;font-weight:600;padding: 10px 5px 10px 27px;text-align:left;\">" +
            												"<span>PRODUCT</span>" +
                                                        "</td>" +
                                                         "<td class=\"heading2\" width=\"20%\" style=\"font-size: 15px;font-family: Georgia, serif;color:#C7272B;font-weight:600;padding: 10px 5px 10px 47px;text-align:left;\" >" +
                                                            "<span>BUY</span>" +
                                                        "</td>" +
                                                        "<td class=\"heading3\" width=\"20%\" style=\"font-size: 15px;font-family: Georgia, serif;color:#C7272B;font-weight:600;padding: 10px 5px 10px 33px;text-align:left;\" >" +
                                                            "<span>SELL</span>" +
                                                        "</td>" +
														 "<td width=\"20%\" style=\"font-size: 15px;font-family: Georgia, serif;color:#C7272B;font-weight:600;padding: 10px 5px 10px 27px !important;text-align:left;\" >" +
                                                            "<span></span>" +
                                                        "</td>" +
														

            //                                                                "<td style=\"width:20%; text-align: center !Important\" >" +
            //                                                                    "<span></span>" +
            //                                                                "</td>" +

            //"<td style=\"width:15%; text-align: center !Important\" >" +
            //   "<span>LOW</span>" +
            //"</td>" +
                                                        "</tr>" +
                                                    "</table>"
            "</td>" +
                                                "</tr>" +
            //Second Row
                                                     " <tr> " +
                                                  " <td> ";
                //messagesDesktopp.length
				
				for (var i = 0; i < messagesDesktopp.length; i++) {
                    //var retDesktop = jQuery.parseJSON(messages[i]);
                    var retDesktop = messagesDesktopp[i].split("\t");
                    var oldRetDesktop;

                    oldRetDesktop = messagesOldDesktop[i].split("\t");
                    if (typeof retDesktop[1] != 'undefined') {

                        //if (deletedScrips[i] != "0") {
                        var buySmall = "";
                        var buyLarge = "";
                        var sellSmall = "";
                        var sellLarge = "";

                        if (retDesktop[3].length == 5) {
                            buySmall = retDesktop[3].substring(0, 2);
                            buyLarge = retDesktop[3].substring(2, 5);
                            buySmall = "";
                            buyLarge = retDesktop[3];
                        }
                        else {

                            buySmall = "";
                            buyLarge = retDesktop[3];

                        }

                        if (retDesktop[4].length == 5) {
                            sellSmall = retDesktop[4].substring(0, 2);
                            sellLarge = retDesktop[4].substring(2, 5);
                            sellSmall = "";
                            sellLarge = retDesktop[4];
                        }
                        else {

                            sellSmall = "";
                            sellLarge = retDesktop[4];

                        }
						var imgbar = "<img src=\"img/menu/gold1.png\" style=\"height: 41px;\" />";
                if (retDesktop[2].toLowerCase().includes("gold")) {
                    imgbar = "<img src=\"img/menu/godlbar.png\" style=\"height: 41px;\" />";

                }
                else if (retDesktop[2].toLowerCase().includes("silver")) {
                    imgbar = "<img src=\"img/menu/silver.png\" style=\"height: 41px;\" />";

                }

                        if (retDesktop[3] > oldRetDesktop[3]) {

                            trowString = trowString +
                            //"<table width=\"100%\"><tr><td onclick=\"callBuySell('" + retDesktop[1] + "')\" >" +
                                            "<table class=\"res_mob_font_width\"  width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\"> " +
                                                "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\" style=\"text-align: center;\"> " +
                                                    "<td class=\"buy_sell_label\" style=\"width:35%;padding: 10px 5px 10px 5px !important;text-align:left;font-weight:750;font-size: 20px ;color:#000;border-right:0px solid #000;border-bottom:0px solid #573c35; " + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td> " +
													//"<td width=\"10%\" class=\"buy_sell_label\">" + imgbar + "</td> "+
                                                    "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
																			"<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: green;color: #fff;display:block;\">" + retDesktop[3] + "</span>" +//<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
																				 "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:red;font-weight:700;\">L:" + retDesktop[6] + "</span>" +
																			"</td>";

                        }
                        else if (retDesktop[3] < oldRetDesktop[3]) {

                            trowString = trowString +
                            //                                "<table width=\"100%\">"+
                            //                                    "<tr>"+
                            //                                        "<td>"+
                                            "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\">" +
                                                "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\"  style=\"text-align: center;\">" +
                                                    "<td class=\"buy_sell_label\" style=\"width:35%; padding: 10px 5px 10px 5px !important;text-align:left;font-weight:750;font-size: 20px ;color:#000;border-right:0px solid #000;border-bottom:0px solid #573c35; " + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td>" +
													// "<td width=\"10%\" class=\"buy_sell_label\">" + imgbar + "</td> "+
                                                    "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
																			"<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: red;color: #fff;display:block;\">" + retDesktop[3] + "</span>" +//<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
																				 "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:red;font-weight:700;\">L:" + retDesktop[6] + "</span>" +
																			"</td>";

                        }
                        else {
                            trowString = trowString +
                            //                                    "<table width=\"100%\">"+
                            //                                        "<tr>"+
                            //                                            "<td>"+
                                                "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\">" +
                                                    "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\"  style=\"text-align: center;\">" +
                                                        "<td class=\"buy_sell_label\" style=\"width:35%;padding: 10px 5px 10px 5px !important;text-align:left;font-weight:750;font-size: 20px ;color:#000;border-right:0px solid #000;border-bottom:0px solid #573c35; " + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td>" +
														//"<td width=\"10%\" class=\"buy_sell_label\">" + imgbar + "</td> "+
                                                        "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
																			"<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: ;color: #000;display:block;\">" + retDesktop[3] + "</span>" +//<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
																				 "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:red;font-weight:700;\">L:" + retDesktop[6] + "</span>" +
																			"</td>";

                        }





                        //For Sell

                        if (retDesktop[4] > oldRetDesktop[4]) {

                            trowString = trowString +


                                                    "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
                                                    "<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: green;color: #fff;display:block;\">" + retDesktop[4] + "</span>" + //<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
                                                    "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:green;font-weight:700;\">H:" + retDesktop[5] + "</span>" +
																			"</td>";
                        }
                        else if (retDesktop[4] < oldRetDesktop[4]) {

                            trowString = trowString +
                            //                                "<table width=\"100%\">"+
                            //                                    "<tr>"+
                            //                                        "<td>"+

                                                   "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
                                                    "<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: red;color: #fff;display:block;\">" + retDesktop[4] + "</span>" + //<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
                                                    "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:green;font-weight:700;\">H:" + retDesktop[5] + "</span>" +
																			"</td>";
                        }
                        else {
                            trowString = trowString +
                            //                                    "<table width=\"100%\">"+
                            //                                        "<tr>"+
                            //                                            "<td>"+

                                                        "<td id=\"" + retDesktop[1] + "SELL\" style=\"width:20%;text-align: center !Important;padding-top: 0px;border-bottom:0px solid #573c35;\">" +
                                                    "<span id=\"mainspan\" style=\"font-size: 28px;font-weight:650;padding: 8px 5px 8px;border-radius:3px;background: ;color: #000;display:block;\">" + retDesktop[4] + "</span>" + //<br/><span style=\"color:#8ce08c;\">H : " + ret[5] + "</span>
                                                    "<span style=\"padding: 3px;font-size: 11px;border-radius:10px;color:green;font-weight:700;\">H:" + retDesktop[5] + "</span>" +
																			"</td>";
                        }

                         trowString = trowString + "<td  class=\"buy_sell_label1\" style=\"width:15%;border: 0;text-align: right;padding: 0;\">" +
                                                "<a style=\"font-size: 20px;color: #C7272B ;border: 2px solid #000;font-weight: 700;padding: 10px 6px;display: block;margin-right:20px;width:150px;text-align:center;background: #F2BBBE;\">BUY</a>" +
                                               "</td>";


                    trowString = trowString + "</tr></table>";


                    }
                    oldDataTrending_SilverRates = data.toString(); //Monank Change

                }
				
				
            } //End If



        } // End -> if (typeof messagesDesktopp != 'undefined') {



        trowString = trowString + "<br><br><br><br><br><br><br><br><br>"; //</td></tr>
		

        trow1_SilverRates = $(trowString);
        trow1_SilverRates.prependTo(zebra1_SilverRates);
        //alert(oldData_Gold_silver_INR_coins);
        oldDataTrending_SilverRates = data.toString();


    }
    catch (e) {
         alert("OnSuccess : " + e);
        oldDataTrending_SilverRates = data.toString();
        //alert(oldData_Gold_silver_INR_coins);
    }


}



//##############################################################################################################33###################################//
// 3 slider data start
function CallWebServiceFromJqueryGoldCoins() {
    try {

        $.ajax({
            type: "GET",
              url: "https://" + localStorage.ipAddressBCast + ":" + localStorage.step3StreamingPort + "/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/rajeshwarcoins",
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccessGoldCoins,
            error: OnErrorGoldCoins,
            cache: false
        });


    }
    catch (e) {
        // alert("CallWebServiceFromJquery " + e);
    }


}



//Gold Coinsssssssssssssssssssssssssss
function OnSuccessGoldCoins(data, status) {
    //alert(data);
    try {


        if (showOnce_coins == "0") {
            showOnce_coins = "1";
        }
        else {

            updateOnlyData_coins(data);
            return;
        }

        var messages = "";
        messages = data.split("\n");

        if (typeof oldDataGoldCoins != 'undefined') {
        }
        else {
            oldDataGoldCoins = data.toString();
        }
        var messagesOldDesktop = oldDataGoldCoins.split("\n");

        if (typeof messages != 'undefined') {
            if (maxRows == 0) {
                maxRows = messages.length;
            }

            gvDataCoins_Trending();
            var zebra1 = "";
            zebra1 = document.getElementById("gvDataCoins"); //Trending Lower Portion
            var trow = "";
            var trowString = "";

            var retDesktop = "";
            retDesktop = messages[0].split("\t");

            if (typeof retDesktop[1] != 'undefined') {

                trowString = trowString + "<table width=\"100%\"> " +
                                                  " <tr> " +
                                                     " <td> " +
                                                         "<table  width=\"100%\" style=\"border-top:2px solid #deb648;border-bottom:2px solid #deb648\"> " +
                                                            "<tr>" +
                                                               "<td style=\"width:70%;\">" +
                                                               "</td>" +

                                                                "<td style=\"width:30%; text-align: center !Important;font-size:17px;font-weight:600\" >" +
                                                                    "<span>PRICE</span>" +
                                                               "</td>" +

                                                             "</tr>" +
                                                           "</table>"
                "</td>" +
                                                        "</tr>" +
                //Second Row
                                                     " <tr> " +
                                                  " <td> ";
                //messages.length

                for (var i = 0; i < messages.length; i++) {
                    //var retDesktop = jQuery.parseJSON(messages[i]);
                    var retDesktop = messages[i].split("\t");
                    var oldRetDesktop;

                    oldRetDesktop = messagesOldDesktop[i].split("\t");
                    if (typeof retDesktop[1] != 'undefined') {

                        //if (deletedScrips[i] != "0") {
                        var buySmall = "";
                        var buyLarge = "";
                        var sellSmall = "";
                        var sellLarge = "";

                        if (retDesktop[3].length == 5) {
                            buySmall = retDesktop[3].substring(0, 2);
                            buyLarge = retDesktop[3].substring(2, 5);
                            buySmall = "";
                            buyLarge = retDesktop[3];
                        }
                        else {

                            buySmall = "";
                            buyLarge = retDesktop[3];

                        }

                        if (retDesktop[4].length == 5) {
                            sellSmall = retDesktop[4].substring(0, 2);
                            sellLarge = retDesktop[4].substring(2, 5);
                            sellSmall = "";
                            sellLarge = retDesktop[4];
                        }
                        else {

                            sellSmall = "";
                            sellLarge = retDesktop[4];

                        }

                        if (retDesktop[3] > oldRetDesktop[3]) {

                            trowString = trowString +

                                            "<table class=\"res_mob_font_width\"  width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\"> " +
                                                "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\" style=\"text-align: center;\"> " +
                                                    "<td class=\"buy_sell_label\" style=\"color:#deb648;width:70%; text-align: left !Important;font-size:;padding:20px 0;" + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td> ";
                            //"<td style=\"width:25%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:#00D600;\">" + buySmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:#00D600;\">" + buyLarge + "</span></td>";

                        }
                        else if (retDesktop[3] < oldRetDesktop[3]) {

                            trowString = trowString +

                                            "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\">" +
                                                "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\"  style=\"text-align: center;\">" +
                                                    "<td class=\"buy_sell_label\" style=\"color:#deb648;width:70%; text-align: left !Important;font-size: ;padding:20px 0;" + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td>";
                            //"<td style=\"width:25%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:red;\">" + buySmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:red;\">" + buyLarge + "</span></td>"

                        }
                        else {
                            trowString = trowString +

                                                "<table class=\"res_mob_font_width\" width=\"100%\" style=\"border-bottom: 2px solid #f1d44c;\">" +
                                                    "<tr onclick=\"callBuySell('" + retDesktop[1] + "','" + retDesktop[2] + "');\"  style=\"text-align: center;\">" +
                                                        "<td class=\"buy_sell_label\" style=\"color:#deb648;width:70%;text-align: left !Important;font-size: ;padding:20px 0;" + Change_ScriptNameFont + ";\">" + retDesktop[2] + "</td>";
                            //"<td style=\"width:25%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:#fff7d8;\">" + buySmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:#fff7d8;\">" + buyLarge + "</span></td>"

                        }





                        //For Sell
                        if (retDesktop[4] > oldRetDesktop[4]) {

                            trowString = trowString +
                                                    "<td id=\"" + retDesktop[1] + "SELLCOINS\" style=\"width:30%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:green;\">" + sellSmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:green;\">" + sellLarge + "</span></td> " +
                                                "</tr> " +
                                            "</table>"
                        }
                        else if (retDesktop[4] < oldRetDesktop[4]) {

                            trowString = trowString +
                                                    "<td id=\"" + retDesktop[1] + "SELLCOINS\" style=\"width:30%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:red;\">" + sellSmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:red;\">" + sellLarge + "</span></td>" +
                                                  "</tr>" +
                                              "</table>"
                        }
                        else {
                            trowString = trowString +
                                                        "<td id=\"" + retDesktop[1] + "SELLCOINS\" style=\"width:30%;text-align: center !Important;\"><span style=\"font-size: large !Important;color:#FFFFFF;\">" + sellSmall + "</span><span style=\"font-size: " + Script_Font_LiveRatesCoins + " !Important;color:#FFFFFF;\">" + sellLarge + "</span></td>" +
                                                     "</tr>" +
                                                 "</table>"
                        }


                    }
                    oldDataGoldCoins = data.toString();

                }
                trowString = trowString + "</td></tr></table>"; //</td></tr>


            } //End If


        } // End -> if (typeof messages != 'undefined') {


        trow = $(trowString);
        trow.prependTo(zebra1);
        //alert(oldData_Gold_silver_INR_coins);
        oldDataGoldCoins = data.toString();


    }
    catch (e) {
        // alert("OnSuccess : " + e);
        oldDataGoldCoins = data.toString();
        //alert(oldData_Gold_silver_INR_coins);
    }



}



function OnErrorGoldCoins(request, status, error) {
    // alert("Webservice Error: " + request.statusText);
}






$(document).ready(function () {
    CallWebServiceFromJqueryLiveRateMessage();
    fnStartClock();
});




function CallWebServiceFromJqueryMarquee() {
    try {
        //alert("CallWebServiceFromJqueryMarquee");
        $.ajax({
            type: "GET",
            //url: "https://" + localStorage.webPanel + "/WebServiceGetMarquee.asmx/getMarquee?username=" + localStorage.appnameWithMiniadminId,
			url: localStorage.ticker1 + localStorage.appnameWithMiniadminId + "/" + "tickerlist1" + localStorage.tickerr1,
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccessMarquee,
            error: OnErrorMarquee,
            cache: false
        });
    }
    catch (e) {
        //alert("CallWebServiceFromJqueryMarquee " + e);
    }


}




function OnSuccessMarquee(data, status) {
    //alert(data);
    try {

       // change start
        var message = data;
			message = message.replace('|','');
			message = message.replace('|','');
			message = message.replace('|','');
			message = message.replace('|','');
		// change end
		

        if (typeof message != 'undefined') {

            removeAllRowsFromMarquee();

            var zebra = document.getElementById("marqueeData");
            var trow;
            var trowString = "";
           // change start
            trowString = trowString + convert(message);
			// change end
			
            //trow = $(trowString);
            //trow.prependTo(zebra);

            $("#marqueeData").html(trowString);
            $('.marquee').marquee({
                //speed in milliseconds of the marquee
                duration: 8000,
                //gap in pixels between the tickers
                gap: 50,
                //time in milliseconds before the marquee will start animating
                delayBeforeStart: 0,
                //'left' or 'right'
                direction: 'left',
                //true or false - should the marquee be duplicated to show an effect of continues fL
                duplicated: true,
                pauseOnHover: true
            });
        }


    }
    catch (e) {
        // alert("OnSuccessMarquee" + e);
    }


}



function OnErrorMarquee(request, status, error) {
    //alert("Webservice Error: " + request.statusText);
}

function removeAllRowsFromMarquee() {

    $("#marqueeData").empty();

}

var convert = function (convert) {

    return $("<span />", { html: convert }).text();

};
