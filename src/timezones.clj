(ns timezones
  (:require [compojure.route :as route]
            [net.cgrand.enlive-html :as html])
  (:use [clojure.contrib.seq-utils :only [positions indexed]]
        clojure.contrib.str-utils
        ring.adapter.jetty
        compojure.core)
  (:gen-class)
  (:import (java.util TimeZone Date Calendar)
           (java.text SimpleDateFormat)
           (java.io StringReader ByteArrayOutputStream
                    PipedOutputStream PipedInputStream)
           (org.apache.batik.dom.svg SVGOMDocument)
           (org.apache.batik.transcoder.image JPEGTranscoder)
           (org.apache.batik.transcoder TranscoderInput TranscoderOutput)))


(def *known-timezones* (set (TimeZone/getAvailableIDs)))

(def *template-file* (or (ClassLoader/getSystemResource "template.tpl")
                         (throw (Exception. "Couldn't find my template file"))))


(def line-style (str "fill:none;stroke:#000000;stroke-width:2px;"
                     "stroke-linecap:butt;stroke-linejoin:miter;"
                     "stroke-opacity:1"))

(def work-hours-style (str "fill:none;stroke:#0000FF;"
                           "stroke-width:4px;stroke-linecap:butt;"
                           "stroke-linejoin:miter;stroke-opacity:1"))

(def point-style "fill:#ff0000;fill-opacity:1")

(def *line-length* 50)
(def *point-size* 5)
(def *line-offset* 120)


(defn draw-line [x y length & [style]]
  {:tag "path"
   :attrs {"style" (or style line-style)
           "d" (format "M %d,%d %d,%d"
                       x y (+ x length) y)}})


(defn draw-point [x y label day]
  [{:tag "circle"
    :attrs {"cx" x
            "cy" y
            "r" *point-size*
            "style" point-style}}
   {:tag "text"
    :attrs {"x" x
            "y" y
            "dx" (format "-%fem" (float (/ (count label) 3)))
            "dy" (* 4 *point-size*)
            "font-family" "Bitstream Vera Sans"
            "font-size" "12px"}
    :content [label]}
   {:tag "text"
    :attrs {"x" x
            "y" y
            "dx" "-2em"
            "dy" (* 8 *point-size*)
            "font-family" "Bitstream Vera Sans"
            "font-size" "10px"}
    :content [day]}])


(defn draw-label [y label]
   {:tag "text"
    :attrs {"x" 5
            "y" y
            "dy" "0.3em"
            "font-family" "Bitstream Vera Sans"
            "font-weight" "bold"
            "font-size" "14px"}
    :content [label]})


(defn draw-work-hours [hours y-offset]
  (map (fn [position]
         (draw-line (+ *line-offset* (* position *line-length*))
                    y-offset
                    *line-length*
                    work-hours-style))
       (remove #(= % (dec (count hours)))
               (positions #(and (>= (:hour %) 7)
                                (< (:hour %) 18))
                          hours))))


(defn format-hour [hour]
  (if (= (:min hour) 0)
    (str (:hour hour))
    (format "%d:%0,2d" (:hour hour) (:min hour))))


(defn name-from-timezone [s]
  (.replace (or (second (.split s "/"))
                s) "_" " "))


(defn draw-timeline [y-offset timeline]
  (concat
   [(draw-line *line-offset* y-offset (* (dec 24) *line-length*))
    (draw-label y-offset (name-from-timezone (:timezone timeline)))]
   (draw-work-hours (:hours timeline) y-offset)
   (mapcat (fn [[idx hour]]
             (draw-point (+ *line-offset* (* idx *line-length*))
                         y-offset
                         (format-hour hour)
                         (:day hour)))
           (indexed (:hours timeline)))))


(defn generate-svg [timezones & [at-date]]
  (let [home-timezone (TimeZone/getTimeZone (first timezones))

        at-date (or at-date
                    (let [now (.getTime (Calendar/getInstance home-timezone))]
                      (.format (java.text.SimpleDateFormat. "yyyy-MM-dd")
                               now)))

        home-start-time (-> (doto (java.text.SimpleDateFormat. "yyyy-MM-dd")
                              (.setTimeZone home-timezone))
                            (.parse at-date))

        timelines (map (fn [timezone]
                         (let [tz (TimeZone/getTimeZone timezone)
                               cal (doto (Calendar/getInstance tz)
                                     (.setTime home-start-time))
                               sdf (doto (java.text.SimpleDateFormat. "dd-MMM")
                                     (.setTimeZone tz))]
                           {:timezone timezone
                            :hours (map (fn [hour]
                                          (let [result {:day (.format sdf (.getTime cal))
                                                        :hour (.get cal Calendar/HOUR_OF_DAY)
                                                        :min (.get cal Calendar/MINUTE)}]
                                            (.add cal Calendar/HOUR_OF_DAY 1)
                                            result))
                                        (range 24))}))
                       timezones)]
    (with-out-str
      (clojure.xml/emit
       {:tag "svg"
        :attrs {"xmlns:dc" "http://purl.org/dc/elements/1.1/"
                "xmlns:svg" "http://www.w3.org/2000/svg"
                "xmlns" "http://www.w3.org/2000/svg"
                "width" (str (+ *line-offset* (* *line-length* 24)))
                "height" (* 100 (inc (count timezones)))
                "id" "svg2"
                "version" "1.1"}
        :content
        (let [[home & tzs] timezones]
          (mapcat (fn [timeline y-offset]
                    (draw-timeline y-offset timeline))
                  timelines
                  (iterate #(+ 100 %) 100)))}))))


(html/defsnippet timezone-entry *template-file*
  [:#timezones [:li (html/nth-of-type 1)]]
  [timezone]
  [:li] (html/content timezone))


(html/deftemplate index *template-file* [timezones]
  [:#timezones] (html/content (map timezone-entry timezones)))


(defn svg-to-jpeg [svg-str]
  (let [transcoder (doto (JPEGTranscoder.)
                     (.addTranscodingHint
                      JPEGTranscoder/KEY_QUALITY
                      (float 1.0)))
        input (TranscoderInput. (StringReader. svg-str))
        pipe-out (PipedOutputStream.)
        pipe-in (PipedInputStream. pipe-out)
        output (TranscoderOutput. pipe-out)]
    (future
      (try
        (.transcode transcoder input output)
        (.close pipe-out)
        (catch Exception e
          (.println System/err e))
        (finally
         (.close pipe-out))))

    pipe-in))


(defroutes main-routes
  (GET "/" {{:strs [zones jpeg at-date at date on]} :params}
       (if zones
         (try (let [svg (generate-svg (.split zones ",")
                                      (or at-date at date on))]
                (if jpeg
                  {:headers {"Content-type" "image/jpeg"}
                   :body (svg-to-jpeg svg)}
                  {:headers {"Content-type" "image/svg+xml"}
                   :body svg}))
              (catch Exception e
                {:headers {"Content-type" "text/plain"}
                 :body "I have no idea what you're talking about."}
                (.printStackTrace e)))
         {:headers {"Content-type" "text/html"}
          :body (index (sort *known-timezones*))}))
  (route/files "/" {:root "static"}))


(defn -main [port]
  (run-jetty (var main-routes) {:port (Integer. port)}))
