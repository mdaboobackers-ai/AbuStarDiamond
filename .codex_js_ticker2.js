$(document).ready(function () {
    CallWebServiceFromJqueryMarquee2();
    var timerMarquee = setInterval("CallWebServiceFromJqueryMarquee2()", 60000);
});




function CallWebServiceFromJqueryMarquee2() {
    try {
        //alert("CallWebServiceFromJqueryMarquee");
        $.ajax({
            type: "GET",
            //url: "https://" + localStorage.webPanel + "/WebServiceGetMarquee.asmx/getMarquee2?username=" + localStorage.appnameWithMiniadminId,
			url: localStorage.ticker1 + localStorage.appnameWithMiniadminId + "/" + "tickerlist2" + localStorage.tickerr1,            
            dataType: "text",
            crossDomain: true,
            processData: false,
            success: OnSuccessMarquee2,
            error: OnErrorMarquee2,
            cache: false
        });
    }
    catch (e) {
        //alert("CallWebServiceFromJqueryMarquee " + e);
    }


}

function OnSuccessMarquee2(data, status) {
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

            removeAllRowsFromMarquee2();

            var zebra = document.getElementById("marqueeData2");
            var trow;
            var trowString = "";
            
			// change start
            trowString = trowString + convert(message);
			// change end

            //trow = $(trowString);
            //trow.prependTo(zebra);

            $("#marqueeData2").html(trowString);
            $('.marquee2').marquee({
                //speed in milliseconds of the marquee
                duration: 5000,
                //gap in pixels between the tickers
                gap: 50,
                //time in milliseconds before the marquee will start animating
                delayBeforeStart: 0,
                //'left' or 'right'
                direction: 'up',
                //true or false - should the marquee be duplicated to show an effect of continues flow
                duplicated: false,
                pauseOnHover: true
            });
        }


    }
    catch (e) {
        // alert("OnSuccessMarquee" + e);
    }


}



function OnErrorMarquee2(request, status, error) {
    //alert("Webservice Error: " + request.statusText);
}

function removeAllRowsFromMarquee2() {

    $("#marqueeData2").empty();

}

var convert = function (convert) {

    return $("<span />", { html: convert }).text();

};







$(document).ready(function () {

    var UserID_M = localStorage.getItem("username");
    var Password_M = localStorage.getItem("password");


    if (UserID_M && Password_M) {
        $(".Loginn").text("LOGOUT");
    }
    else {
        $(".Loginn").text("LOGIN");
    }



});

