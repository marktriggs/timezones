(ns timezones
  (:require [compojure.route :as route]
            [net.cgrand.enlive-html :as html])
  (:use [clojure.contrib.seq-utils :only [positions indexed]]
        clojure.contrib.str-utils
        ring.adapter.jetty
        compojure.core)
  (:gen-class)
  (:import (java.util TimeZone)
           (java.text SimpleDateFormat)
           (java.io StringReader ByteArrayOutputStream
                    PipedOutputStream PipedInputStream)
           (org.apache.batik.dom.svg SVGOMDocument)
           (org.apache.batik.transcoder.image JPEGTranscoder)
           (org.apache.batik.transcoder TranscoderInput TranscoderOutput)))


(def *known-timezones* (set (TimeZone/getAvailableIDs)))

(def *template-file* (or (ClassLoader/getSystemResource "template.tpl")
                         (throw (Exception. "Couldn't find my template file"))))


(defn get-tz-offset [tz at-time]
  (if (*known-timezones* tz)
    (/
     (.getOffset (TimeZone/getTimeZone tz)
                 at-time)
     1000
     60
     60)
    (throw (Exception. "Unknown timezone"))))


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


(defn draw-point [x y label]
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
    :content [label]}])


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
               (positions #(and (>= % 7)
                                (< % 18))
                          hours))))


(defn format-hour [hour]
  (if (= (int hour) hour)
    (str hour)
    (let [h (int hour)
          m (- (float hour) h)]
      (format "%d:%0,2d" h (int (* m 60))))))


(defn draw-timeline [y-offset hours label]
  (concat
   [(draw-line *line-offset* y-offset (* (dec 24) *line-length*))
    (draw-label y-offset label)]
   (draw-work-hours hours y-offset)
   (mapcat (fn [[idx hour]]
             (draw-point (+ *line-offset* (* idx *line-length*))
                         y-offset
                         (format-hour hour)))
           (indexed hours))))


(defn name-from-timezone [s]
  (.replace (or (second (.split s "/"))
                s) "_" " "))


(defn generate-svg [timezones & [at-date]]
  (let [at-ms (if at-date
                (.getTime (.parse (SimpleDateFormat. "yyyy-MM-dd")
                                  at-date))
                (System/currentTimeMillis))]
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
          (concat (draw-timeline 100 (range 24) (name-from-timezone home))
                  (mapcat (fn [tz y-offset]
                            (draw-timeline
                             y-offset
                             (map #(mod % 24)
                                  (take 24
                                        (iterate inc
                                                 (- 0
                                                    (- (get-tz-offset home
                                                                      at-ms)
                                                       (get-tz-offset tz
                                                                      at-ms))))))
                             (name-from-timezone tz)))
                          tzs
                          (take (count tzs) (iterate #(+ 100 %) 200)))))}))))



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
              (catch Exception _
                {:headers {"Content-type" "text/plain"}
                 :body "I have no idea what you're talking about."}))
         {:headers {"Content-type" "text/html"}
          :body (index (sort *known-timezones*))}))
  (route/files "/" {:root "static"}))


(defn -main [port]
  (run-jetty (var main-routes) {:port (Integer. port)}))
