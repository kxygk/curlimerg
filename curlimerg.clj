(ns curlimerg
  (:require [babashka.http-client :as http]
            [tick.core       :as tick]
            [tick.alpha.interval]
            [babashka.curl :as curl]
            [clojure.java.io :as io]))

(defn
  download-day-file
  "Daily files are of the form
  location: ftp://arthurhouftps.pps.eosdis.nasa.gov/sm/730/gpmdata/2011/08/01/gis/
  file:     3B-DAY-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.6360.V07B.tif
  The `DAY` part indicates it's a daily aggregate image"
  [date-inst
   & [{:keys [directory-prefix
              directory-suffix
              file-prefix
              file-time
              file-version
              file-extension
              username
              password]
       :or   {directory-prefix "ftp://arthurhouftps.pps.eosdis.nasa.gov/sm/730/gpmdata/"
              ;; file ex: "3B-DAY-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.6360.V07B.tif"
              directory-suffix "/gis/"
              file-prefix      "3B-DAY-GIS.MS.MRG.3IMERG." ;; daily
              file-time        "-S000000-E235959" ;; always the same for daily files
              ;; 6360 - weird day suffix that is 30 * DAY-OF-YEAR
              file-version     "V07B"
              file-extension   "tif"
              username         ""
              password         ""}}]]
  (let [date (->> date-inst
                  tick/date )]
    (let [weird-day-suffix (->> (tick.alpha.interval/new-interval (-> date
                                                                      tick/year) ;; ugly .. to get day of year
                                                                  date)
                                tick/duration
                                tick/days
                                dec
                                (* 30)
                                (format "%04d"))]
      (let [file-path (str directory-prefix
                           (tick/year date)
                           "/"
                           (->> date ;; `tick` can't get month's number directly
                                (tick/format (tick/formatter "MM")))
                           "/"
                           (->> date ;; `tick` unlike `tick/day-of-month` preserves leading `0`
                                (tick/format (tick/formatter "dd")))
                           directory-suffix
                           file-prefix
                           (->> date
                                (tick/format (tick/formatter "yyyyMMdd")))
                           file-time
                           "."
                           weird-day-suffix
                           "."
                           file-version
                           "."
                           file-extension)]
        (println "\nFile being downloaded:\n"
                 file-path)
        (io/copy (-> file-path
                     (curl/get {:as       :bytes
                                :raw-args ["-4"
                                           "--ftp-ssl"
                                           "--user"
                                           (str username
                                                ":"
                                                password)]})
                     :body)
                 (io/file (str (tick/format (tick/formatter "yyyy-MM-dd")
                                            date)
                               ".tif")))))))
#_
(download-day-file #inst"2011-08-01"
                   {:username "dummy"
                    :password "dummy"})
(defn
  download-imerg-days
  "download from `start-date` to `end-date`
  dates inclusive"
  [start-date-inst
   end-date-inst]
  (download-day-file start-date-inst
                     {:username "username"
                      :password "password"})
  (if (= :equals
         (tick.alpha.interval/relation (tick/date start-date-inst)
                                       (tick/date end-date-inst)))
    nil
    (recur (tick/inc (tick/date start-date-inst)) ;; converts to `LocalDate` .. but seems to still work
           end-date-inst)))
#_
(download-imerg-days #inst"2011-12-28"
                     #inst"2012-01-03")


;; = `Rain`
;; == `IMERG`
;;  Now we want to read in gridded satellite rain data and overlay it on to our maps. FINAL Rain data can be retrieved from `arthurhou.pps.eosdis.nasa.gov` and requires an account with them
;;
;; The path on the server will look something like this:
;;
;; `ftp://arthurhou.pps.eosdis.nasa.gov/sm/730/gpmdata/2011/08/01/gis/3B-MO-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.08.V06B.tif`
;;
;;
;; EARLY data (not normalized with rain gauges)
;; is available on a separate server:
;; `https://jsimpsonhttps.pps.eosdis.nasa.gov`
;; The registration is the same at:
;; `https://registration.pps.eosdis.nasa.gov/registration`
;; You just need to indicate you want access to *Near-Real Time (NRT)* data
;;
;; An in depth explanation of the data is in a PDF here: https://gpm.nasa.gov/resources/documents/imerg-geotiff-worldfile-documentation
;;
;; While the document focuses on the `GeoTIFF` format, it also provides all the necessary background to understand how everything fits together. It's a very good write up. Here I just provide a very short summary.
;;
;; Looking at the file/path in parts:
;;
;; sm/730/;: unclear
;; gpmdata/:: GPM is the current satellite system that synthesizes different data to give these precipitation maps
;; gis:: means GeoTIFF - b/c the original meteorology format is "HDF5" and it's not GIS-software-friendly
;;
;; Finally these folders are organized by days. Each day's folder will have 48 half-hour snapshots of the precipitation. These come in a format that looks like `3B-HHR-…`
;; Each day also has a *cumulative GeoTiff* will a sum of all the precipitation for that day. This file is named `3B-DAY-…`
;; The monthly precipitation is hidden away in the first day of the month's folder :). Its format will be `3B-MO-…` All the other days of the month won't have this file. Yeah... it took a while to figure that one out.
;;
;; The data can then be downloaded using https://github.com/curl/curl[CURL]. The username and passwords provided by the service is very inconvenient b/c they contain an `@` symbol, so you need to pass them in explicitely using the `--user` flag:
;;
;; [,sh]
;; curl -4 --ftp-ssl --user <<email>>:<<email>> ftp://arthurhouftps.pps.eosdis.nasa.gov/sm/730/gpmdata/2011/08/01/gis/3B-MO-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.08.V06B.tif -o output.tif
;;
;;  We can do the same programmatically in Clojure

;; Based off of libary example:
;; https://github.com/lsevero/clj-curl/blob/master/examples/simple_ftp.clj
