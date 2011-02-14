<html>

  <head>  
    <link type="text/css" href="css/ui-lightness/jquery-ui-1.8.9.custom.css" rel="stylesheet" />	
    <script type="text/javascript" src="js/jquery-1.4.4.min.js"></script>
    <script type="text/javascript" src="js/jquery-ui-1.8.9.custom.min.js"></script>

    <style>
      #timezones, #targets { list-style-type: none; margin: 0; padding: 0; float: left; margin-right: 10px; }
      #timezones li, #targets li { margin: 0 5px 5px 5px; padding: 5px; font-size: 1.2em; width: 300px; }
    </style>
    <script>
      $(function() {
        $( "#timezones, #targets" ).sortable({
          connectWith: ".connectedSortable"
        }).disableSelection();
      });

      $(function () {
        $("#timezones li").dblclick(function (event) {
          $("#targets").append (event.currentTarget);
          $("#timezones").remove (event.currentTarget);
        })
      });

      $(function () {
        $("#go").click(function (event) {
          var timezones = $("#targets li").filter(".timezone");
          if (timezones.length > 0) {
	    var arr = timezones.map (function (idx, elt) {
	      return $(elt).text ();
	    });

            var options = [];
            options.push ('zones=' + arr.toArray ().join (","));

            if ($("#use-jpeg").is (':checked')) {
                options.push ('jpeg=true');
            }

            if ($("#at-date").val ()) {
                options.push ('at-date=' + $("#at-date").val ());
            }


            window.location.href = '?' + options.join ("&");
          }
        })
      });

    </script>
  </head>
  <body>


    <div class="demo">

      <ul id="timezones" class="connectedSortable">
	<li class="timezone ui-state-default"></li>
      </ul>

      <div>
	<div>
	  <div>Drag and order your timezones here<br><small>(or double-click them if you like)</small></div>
	  <ul id="targets" class="connectedSortable">
	    <li id="dummy" class="ui-state-highlight"></li>
	  </ul>
	</div>
	<div style="width: 250px; float: left;">
	  <div><label><small>Show timezones as at a certain date (yyyy-mm-dd)</small></label></div>
	  <div><input id="at-date" type="text" value=""></div>
	  <div><input id="use-jpeg" type="checkbox">Generate JPEG</div>
	  <div style="margin-top: 10px;"><input id="go" type="button" value="Show timezones"></div>
	</div>

      </div>

    </div><!-- End demo -->

  </body>
</html>
