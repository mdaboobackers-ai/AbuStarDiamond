
function removeAllRowsFromTable_HeadRates() {
    $("#Header_rates").empty();
    $("#Header_rates2").empty();
}




function fnStartClock_HeadRates() {

    try {
        //alert("fnStartClock");
        FunctionTodayDate();
        oInterval = setInterval("refreshData_HeadRates()", 60000);
		refreshData_HeadRates();
    }
    catch (e) {
    }
}

function refreshData_HeadRates() {
    CallWebServiceFromJquery_HeadRates();
}

function fnStopClock_HeadRates() {
    try {
        clearInterval(oInterval);
    }
    catch (e) {
    }
}


function addZero_HeadRates(i) {
    if (i < 10) {
        i = "0" + i;
    }
    return i;
}

function updateTime_HeadRates() {
    var d = new Date();
    var x = document.getElementById("cur_time");
    var h = addZero_HeadRates(d.getHours());
    var m = addZero_HeadRates(d.getMinutes());
    var s = addZero_HeadRates(d.getSeconds());
    var ampm = h >= 12 ? 'pm' : 'am';
    h = h % 12;
    h = h ? h : 12; // the hour '0' should be '12'
    x.innerHTML = h + ":" + m + ":" + s;
}

var maxRows_HeadRates = 0;
var oldData_HeadRates;
var counterRefresh_HeadRates = 0;
var today_date;

function FunctionTodayDate() {
    var today = new Date();
    var dd = today.getDate();
    var mm = today.getMonth() + 1; //January is 0!
    var yyyy = today.getFullYear();

    if (dd < 10) {
        dd = '0' + dd
    }

    if (mm < 10) {
        mm = '0' + mm
    }

    today = dd + '-' + mm + '-' + yyyy;
    today_date = today;
    //    document.write(today);
}

function CallWebServiceFromJquery_HeadRates() {
    try {
        $.ajax({
            type: "GET",
            url: "https://" + localStorage.ipAddressBCast + ":" + localStorage.step3StreamingPort+"/VOTSBroadcastStreaming/Services/xml/GetLiveRateByTemplateID/slnmjdma",
            //url: "http://websitebroadcast.arihantspot.com:8888/VOTSBroadcast/Services/xml/GetLiveRate",
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccess_HeadRates,
            error: OnError_HeadRates,
            cache: false
        });
    }
    catch (e) {

    }

}


function OnSuccess_HeadRates(data, status) {
    //alert(data);
    try {



        var messagesDesktopp_HeadRates = "";
        messagesDesktopp_HeadRates = data.split("\n");
        //alert(messagesDesktopp_HeadRates.length);
        if (typeof oldData_HeadRates != 'undefined') {

        }
        else {
            //alert("1");
            oldData_HeadRates = data.toString();
        }
        var messagesOldDesktop_HeadRates = oldData_HeadRates.split("\n");

        if (typeof messagesDesktopp_HeadRates != 'undefined') {
            if (maxRows_HeadRates == 0) {
                maxRows_HeadRates = messagesDesktopp_HeadRates.length;
            }

            removeAllRowsFromTable_HeadRates();
            var zebra_HeadRates = "";
            zebra_HeadRates = document.getElementById("Header_rates"); //Desktopppppppppppppppppppppppppppp            

            var zebra_HeadRates2 = "";
            zebra_HeadRates2 = document.getElementById("Header_rates2"); //Desktopppppppppppppppppppppppppppp            

            var trow_HeadRates = "";
            var trow_HeadRates2 = "";
            //GOLD
            var retDesktop_HeadRates = "";
            retDesktop_HeadRates = messagesDesktopp_HeadRates[0].split("\t");
            var oldRetDesktop_HeadRates = "";
            var trowString_HeadRates = "";
            oldRetDesktop_HeadRates = messagesOldDesktop_HeadRates[0].split("\t");



            trowString_HeadRates = trowString_HeadRates +
            //                    "<table class=\"table table_responstive1\">";
                "<table width=\"100%\" class=\"hed_rate\">";

            trowString_HeadRates = trowString_HeadRates + "<tr>" +
			                                "<th class=\"mjdma text-center\" colspan=\"2\">" +
				                                "<strong>MJDTA RATE " + today_date + "</strong>" +
			                                "</th>" +
		                                "</tr>";



            if (typeof retDesktop_HeadRates[1] != 'undefined') {

                trowString_HeadRates = trowString_HeadRates + "<tr class=\"rate_top\">" +
			                                    "<td width=\"70%\" class=\"bod-right text-left\">" +
				                                    "<span class=\"chennai_rate\">" + retDesktop_HeadRates[2] + "</span>" +
			                                    "</td>";


                if (retDesktop_HeadRates[4] > oldRetDesktop_HeadRates[4]) {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"timing2\" style=\"color:#00D600\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }
                else if (retDesktop_HeadRates[4] < oldRetDesktop_HeadRates[4]) {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"timing2\" style=\"color:red\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }
                else {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"timing2\" style=\"color:#000\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }

                trowString_HeadRates = trowString_HeadRates + "</tr>";


            }


            //SILVER
            retDesktop_HeadRates = messagesDesktopp_HeadRates[1].split("\t");
            oldRetDesktop_HeadRates = messagesOldDesktop_HeadRates[1].split("\t");
            if (typeof retDesktop_HeadRates[1] != 'undefined') {


                trowString_HeadRates = trowString_HeadRates + "<tr class=\"rate_bottom\">" +
			                                    "<td width=\"70%\" class=\"bod-right text-left\">" +
				                                    "<span class=\"silverate_head \">" + retDesktop_HeadRates[2] + "</span>" +
			                                    "</td>";


                if (retDesktop_HeadRates[4] > oldRetDesktop_HeadRates[4]) {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"silverrate2\" style=\"color:#00D600\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }
                else if (retDesktop_HeadRates[4] < oldRetDesktop_HeadRates[4]) {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"silverrate2\" style=\"color:red\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }
                else {
                    trowString_HeadRates = trowString_HeadRates + "<td width=\"30%\" class=\"text-right\">" +
				                                    "<span class=\"silverrate2\" style=\"color:#000\">" + retDesktop_HeadRates[4] + "</span>" +
			                                    "</td>";
                }

                trowString_HeadRates = trowString_HeadRates + "</tr>";

            }




            trowString_HeadRates = trowString_HeadRates +
                       "</table>";


            trow_HeadRates = $(trowString_HeadRates);
            trow_HeadRates.prependTo(zebra_HeadRates);

            trow_HeadRates2 = $(trowString_HeadRates);
            trow_HeadRates2.prependTo(zebra_HeadRates2);

        }

        if (counterRefresh_HeadRates == 0) {
            counterRefresh_HeadRates = counterRefresh_HeadRates + 1;
        }
        oldData_HeadRates = data.toString();

    }
    catch (e) {
        oldData_HeadRates = data.toString();

    }


}
function OnError_HeadRates(request, status, error) {
}



$(document).ready(function () {
    fnStartClock_HeadRates();
});





