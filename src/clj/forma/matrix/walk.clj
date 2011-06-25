(ns forma.matrix.walk
  (:use cascalog.api
        [forma.utils :only (nth-in)]
        [forma.matrix.utils :only (insert-into-val
                                   insert-at)]))

(defn walk-matrix
  "Walks along the rows and columns of a matrix at the given window
  size, returning all (window x window) snapshots."
  [m window]
  (mapcat (comp (partial apply map vector)
                (partial map (partial partition window 1)))
          (partition window 1 m)))

(defn buffer-matrix
  "For the supplied matrix `mat`, returns a new matrix generated by
  padding the beginning and end of each row and column with the
  supplied value, repeated `buffer-size` times. For example:

  (def test-matrix [[0 1 2]
                    [3 4 5]])

  (buffer-matrix 2 1 test-matrix)
  => [[1 1 1 1 1 1 1]
      [1 1 1 1 1 1 1]
      [1 1 0 1 2 1 1]
      [1 1 3 4 5 1 1]
      [1 1 1 1 1 1 1]
      [1 1 1 1 1 1 1]]"
  [buffer-size val [row :as mat]]
  (let [n (-> buffer-size (* 2) (+ (count row)))
        buf-row (repeat n val)]
    (insert-at buffer-size
               (for [row mat]
                 (insert-into-val val buffer-size n row))
               (repeat (* 2 buffer-size) buf-row))))

;; TODO: Docs
(defn wipe-out
  "Takes a nested collection and a sequence of keys, and returns the
  collection sans the value located at the position indicated by the
  supplied key sequence."
  [coll [k & ks]]
  (concat (take k coll)
          (when ks [(wipe-out (nth coll k) ks)])
          (drop (inc k) coll)))

(defn neighbor-scan
  "Returns a lazy sequence of elements, along with a sequence of
  non-nil neighbors."
  [num-neighbors mat]
  {:pre [(> (count mat)
            (inc (* 2 num-neighbors)))]}
  (let [window (inc (* 2 num-neighbors))
        new-mat (buffer-matrix num-neighbors nil mat)]
    (for [sub-win (walk-matrix new-mat window)]
      ((juxt nth-in wipe-out) sub-win [num-neighbors num-neighbors]))))

(defn windowed-function
  "apply a function `fn` to each element in a matrix `mat` over a moving
  window, defined by the number of neighbors."
  [num-neighbors f mat]
  {:pre [(> (count mat)
            (inc (* 2 num-neighbors)))]}
  (let [window  (+ 1 (* 2 num-neighbors))
        new-mat (buffer-matrix num-neighbors nil mat)]
    (for [sub-win (walk-matrix new-mat window)]
      (->> sub-win
           (apply concat)
           (filter (complement nil?))
           (apply f)))))

