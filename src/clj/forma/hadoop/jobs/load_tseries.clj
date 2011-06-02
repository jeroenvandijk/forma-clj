(ns forma.hadoop.jobs.load-tseries
  (:use cascalog.api
        [forma.date-time :only (datetime->period current-period)]
        [forma.source.modis :only (tile-position
                                   tilestring->hv)]
        [forma.trends :only (timeseries)])
  (:require [forma.hadoop.io :as io]
            [cascalog.ops :as c]
            [forma.hadoop.predicate :as p])
  (:gen-class))

(def
  ^{:doc "Predicate macro aggregator that generates a timeseries, given
  `?chunk`, `?temporal-resolution` and `?date`. Currently only
  functions when `?chunk` is an instance of `forma.schema.DoubleArray`."}
  form-tseries
  (<- [?temporal-res ?date ?chunk :> ?pix-idx ?t-start ?t-end ?tseries]
      (datetime->period ?temporal-res ?date :> ?tperiod)
      (:sort ?tperiod)
      (timeseries ?tperiod ?chunk :> ?pix-idx ?t-start ?t-end ?tseries)))

(defn extract-tseries
  [chunk-source]
  (<- [?dataset ?s-res ?t-res ?tilestring ?chunk-size ?chunkid ?pix-idx ?t-start ?t-end ?tseries]
      (chunk-source ?dataset ?s-res ?t-res ?tilestring ?date ?chunkid ?chunk)
      (io/num-doubles ?chunk :> ?chunk-size)
      (form-tseries ?t-res ?date ?chunk :> ?pix-idx ?t-start ?t-end ?tseries)))

(defn process-tseries
  "Given a source of chunks, this subquery generates timeseries with
  all relevant accompanying information."
  [tseries-source]
  (<- [?dataset ?s-res ?t-res ?tile-h ?tile-v ?sample ?line ?t-start ?t-end ?tseries]
      (tseries-source ?dataset ?s-res ?t-res ?tilestring
                      ?chunk-size ?chunkid ?pix-idx ?t-start ?t-end ?tseries)
      (tilestring->hv ?tilestring :> ?tile-h ?tile-v)
      (tile-position ?s-res ?chunk-size ?chunkid ?pix-idx :> ?sample ?line)))

(defn -main
  "TODO: Docs.

  Sample usage:

      (-main \"s3n://redddata/\" \"/timeseries/\" \"ndvi\"
             \"1000-32\" [\"008006\" \"008009\"])"
  [base-input-path output-path & pieces]
  (let [pieces (map read-string pieces)
        chunk-source (apply io/chunk-tap base-input-path pieces)]
    (?- (hfs-seqfile output-path)
        (-> chunk-source
            extract-tseries
            process-tseries))))
