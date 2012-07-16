(ns forma.schema
  (:require [forma.date-time :as date]
            [forma.reproject :as r]
            [forma.thrift :as thrift]            
            [forma.utils :as u]
            [forma.thrift :as thrift]
            [clojure.string :as s]))

(defn create-timeseries
  "Create a TimeSeries from a period start index and a collection of timeseries
   values. The period end index is calculated by adding the size of the
   collection to the period start index."
  ([start-idx series]
     {:pre [(> start-idx 0) (coll? series)]}
     (when series
       (let [elems (count series)]
         (create-timeseries start-idx (dec (+ start-idx elems)) series))))
  ([start-idx end-idx series]
     {:pre [(> start-idx 0) (<= start-idx end-idx) (coll? series)]}
     (when series
       (thrift/TimeSeries* start-idx end-idx series))))

(defn boundaries
  "Accepts a sequence of pairs of <initial time period, collection>
  and returns the maximum start period and the minimum end period. For
  example:

    (boundaries [0 [1 2 3 4] 1 [2 3 4 5]]) => [1 4]"
  [pair-seq]
  {:pre [(even? (count pair-seq))]}
  (reduce (fn [[lo hi] [x0 ct]]
            [(max lo x0) (min hi ct)])
          (for [[x0 seq] (partition 2 pair-seq)]
            [x0 (+ x0 (count seq))])))

(defn adjust
  "Appropriately truncates the incoming timeseries values (paired with
  the initial integer period), and outputs a new start and both
  truncated series. For example:

    (adjust 0 [1 2 3 4] 1 [2 3 4 5])
    ;=> (1 [2 3 4] [2 3 4])"
  [& pairs]
  {:pre [(even? (count pairs))]}
  (let [[bottom top] (boundaries pairs)]
    (cons bottom
          (for [[x0 seq] (partition 2 pairs)]
            (into [] (u/trim-seq bottom top x0 seq))))))

(defn fire-series
  "Creates a `FireSeries` object from the supplied sequence of
  `FireValue` objects."
  ([start xs]
     (fire-series start
                  (dec (+ start (count xs)))
                  xs))
  ([start end xs]
     (thrift/TimeSeries* start end xs)))

(defn adjust-fires
  "Returns a TimeSeries object of fires that are within the bounds of
  the interval defined by :est-start, :est-end, and :t-res.  Within
  the FORMA workflow, this function is applied to a series of
  FireValues that is longer (on the tail-end or both the tail- and
  front-end) of the interval based on the info in the estimation
  parameter map.  This series may not have any actual fire hits in it;
  but rather is a vector of zero-value fires -- a sort of sparse
  vector where each element is a FireValue.

  TODO: preconditions to test that the supplied series matches the
  description above.  Then adjust the description to be shorter, more
  understandable."
  [{:keys [est-start est-end t-res]} f-series]
  (let [[f-start f-end arr-val] (thrift/unpack f-series)
        series (thrift/unpack arr-val)
        [start end] (map (partial date/datetime->period t-res)
                         [est-start est-end])]
    [(->> series
          (u/trim-seq start (inc end) f-start)
          (thrift/TimeSeries* start))]))

(defn add-fires
  "Returns a new `FireValue` object generated by summing up the fields
  of each of the supplied `FireValue` objects."
  [& f-tuples]
  (->> f-tuples
       (map thrift/unpack)
       (apply map +)
       (apply thrift/FireValue*)))

(defn neighbor-value
  "Accepts either a forma value or a sequence of sub-values."
  ([forma-val]
     (let [[fire short param long t-stat] (thrift/unpack forma-val)]
       (thrift/NeighborValue* fire 1 short short long long t-stat t-stat param param)))
  ([fire neighbors avg-short
    min-short avg-long min-long avg-stat min-stat avg-param min-param]
     (thrift/NeighborValue* fire neighbors avg-short min-short avg-long min-long
                            avg-stat min-stat avg-param min-param)))

(defn merge-neighbors
  "Merges the supplied instance of `FormaValue` into the existing
  aggregate collection of `FormaValue`s represented by `neighbor-val`.
  Returns a new neighbor value object representing the merged values."
  [neighbor-val forma-val]
  {:pre [(instance? forma.schema.NeighborValue neighbor-val)]}
  (let [[fire short param long t] (thrift/unpack forma-val)        
        [n-fire ncount avg-short min-short avg-long
         min-long avg-stat min-stat ave-break min-break] (thrift/unpack neighbor-val)]
    (thrift/NeighborValue* (add-fires n-fire fire)
                           (inc ncount)
                           (u/weighted-mean avg-short ncount short 1)
                           (min min-short short)
                           (u/weighted-mean avg-long ncount long 1)
                           (min min-long long)
                           (u/weighted-mean avg-stat ncount t 1)
                           (min min-stat t)
                           (u/weighted-mean ave-break ncount param 1)
                           (min min-break param))))

(defn combine-neighbors
  "Returns a new forma neighbor value generated by merging together
   each entry in the supplied sequence of forma values.  See tests for
   example usage."
  [[x & more]]
  (if x
    (reduce merge-neighbors (neighbor-value x) more)
    (thrift/NeighborValue* (thrift/FireValue* 0 0 0 0)
                           (long 0) 0. 0. 0. 0. 0. 0. 0. 0.)))

(defn forma-value
  "Returns a vector containing a FireValue, short-term drop,
  parametrized break, long-term drop and t-stat of the short-term
  drop."
  [fire short param-break long t-stat]
  (let [fire (or fire (thrift/FireValue* 0 0 0 0))]
    (thrift/FormaValue* fire short param-break long t-stat)))

(defn fires-cleanup
  "If the fire-series is nil, leave it be - it'll be handled in
   `forma-seq-non-thrift`. Else, unpack it"
  [fire-series]
  (if (nil? fire-series)
    fire-series
    (thrift/unpack (thrift/get-series fire-series))))

(defn forma-seq
  "Accepts 5 timeseries of equal length and starting position, each
   representing a time-indexed series of features for a given pixel.
   Returns the tranposition: a single timeseries of
   FormaValues.

  `fire-series` gets special treatment because it could come into
   `forma-seq` as nil (i.e. no fires for a given pixel) per the
   forma-tap query in forma.clj; fires is an ungrounded variable in
   the cascalog query, forma-tap"
  [fire-series short-series long-series t-stat-series break-series]
  (let [fire-clean (fires-cleanup fire-series)
        shorts (vec (reductions min short-series))
        breaks (vec (reductions max break-series))]
    [(->> (concat [fire-clean] [shorts] [long-series] [t-stat-series] [breaks])
          (map #(or % (repeat %)))
          (apply map forma-value)
          (vec))]))
