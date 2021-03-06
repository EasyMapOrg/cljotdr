(ns cljotdr.supparams
  (:require [cljotdr.utils :refer :all])
  (:gen-class))

(defn- fields
  [fmtno]
  (list
   "supplier", ; ............. 0
   "OTDR", ; ................. 1
   "OTDR S/N", ; ............. 2
   "module", ; ............... 3
   "module S/N", ; ........... 4
   "software", ; ............. 5
   "other", ; ................ 6
   )
  )

(defn- read-field
  [raf fmtno field]
  (get-string raf)
  )

(defn- dump
  "dump results to screen"
  [results fmtno]
  (if (get results "debug")
    (let [ block (get results "SupParams") ]
      (doall
       (map-indexed
        (fn [i x]
          (println (format "    : %d. %s:" i x) (get block x))
          )
        (fields fmtno)
        ) ; map
       ) ; doall
      ) ; let
    ) ; if
  )


(defn process
  "process SupParams block"
  [raf fmtno bname pos bsize results]
  (.seek (raf :fh) pos)
  
  (if (get results "debug")
    (do
      (println "")
      (println (format "MAIN:  %s block: %d bytes, start pos 0x%X (%d)"
                       bname bsize pos pos))
      ) ; do
    ) ; if
  ;; get block header
  (if (= fmtno 2)
    (let [ _bname_ (get-string raf)]
      (if (not= bname _bname_)
        (println "!!!" bname "block header does not match! is " _bname_)
        ) ; if
      ) ; let
    ) ; if
  
  ;; process each field
  (loop [
         flist (fields fmtno)
         current results
         ]
    (if (empty? flist)
      (do
        (dump current fmtno)
        ;; return
        current
        )
      (let [field (first flist)]
        ;;
        (recur
         (rest flist)
         (assoc-in current [bname field]
                   (read-field raf fmtno field)
                   )
         ) ; recur
        ) ; let
      ) ; if
    ) ; loop
  )

;; ========================================================

(defn- real-alter-block
  [bname fmtno old-map new-map input output]

  (println "* Proceesing/altering " bname)
  (let [startpos (.getFilePointer (output :fh))]
    (if (= fmtno 2) ; write header
      (write-string output bname)
      )
    
    (loop [
           flist (fields fmtno)
           ]
      (if (empty? flist) nil
          (let [field (first flist)
                oldval (get-in old-map [bname field])
                tmpval (get-in new-map [bname field])
                newval (if (nil? tmpval) oldval tmpval)
                ]
            (write-string output newval)
            (recur (rest flist))
            ); let
          ); if
      ) ;loop
    
    ;; (println "\tDEBUG: " bname " block: loop finished")
    (let [
          currpos  (.getFilePointer (output :fh))
          newbsize (- currpos startpos)
          mbsize   (get-in old-map ["mapblock" "nbytes"])
          ]
      ;; (println "Old block size " (get-in old-map ["blocks" bname "size"]))
      ;; (println "New block size " newbsize)
      
      (cljotdr.mapblock/adjust-block-size bname newbsize mbsize output)
      
      (.seek (output :fh) currpos) ;; restore file position for next round
      ); let (adjust-block-size)
    
    ); let (startpos)
  )

(defn alter-block
  [bname fmtno old-map new-map input output]
  (if (not= bname "SupParams") (println "! wrong block " bname)
      (real-alter-block bname fmtno old-map new-map input output)
      )
  )
