
This is a quick hack to display time lines of different timezones for
easy comparison.  Handy if you're working with people from multiple
different timezones and want to see how your hours line up.  Makes use
of Java's timezone data to hopefully handle daylight savings
correctly.

![screenshot](https://github.com/marktriggs/timezones/raw/master/example.jpg)

Behind the scenes this is implemented using Compojure, Enlive (for
templating), Clojure's built-in XML functionality, JQuery and Batik
(for generating JPEG versions of the time lines).

To build it it's the usual:

  1.  Get Leiningen from http://github.com/technomancy/leiningen and put
      the 'lein' script somewhere in your $PATH.

  2.  From the checkout directory directory, run `lein uberjar'.  Lein
      will grab all required dependencies and produce a standalone
      uberjar.

I run my instance with:

     #!/bin/bash

     cd /path/to/checkout
     java -jar path-to-uberjar 8081

All pretty messy, but that's what quick hacks are for!
