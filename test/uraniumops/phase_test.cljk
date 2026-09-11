(ns uraniumops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:flag-radiological-concern` and `:coordinate-shipment`
  must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [uraniumops.phase :as phase]))

(deftest always-escalate-ops-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits a radiological-concern flag or a shipment coordination"
    (doseq [[n {:keys [auto]}] phase/phases
            op [:flag-radiological-concern :coordinate-shipment]]
      (is (not (contains? auto op))
          (str "phase " n " must not auto-commit " op)))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest extraction-logging-enabled-from-phase-1
  (is (contains? (:writes (get phase/phases 1)) :log-extraction-record))
  (is (not (contains? (:writes (get phase/phases 0)) :log-extraction-record))))

(deftest scheduling-and-shipment-enabled-from-phase-2
  (doseq [op [:schedule-mining-operation :coordinate-shipment]]
    (is (contains? (:writes (get phase/phases 2)) op))
    (is (not (contains? (:writes (get phase/phases 1)) op)))))

(deftest radiological-concern-enabled-only-from-phase-3
  (is (contains? (:writes (get phase/phases 3)) :flag-radiological-concern))
  (is (not (contains? (:writes (get phase/phases 2)) :flag-radiological-concern))))

(deftest phase-3-auto-commits-exactly-two-of-four-ops
  (testing ":flag-radiological-concern and :coordinate-shipment are never auto-eligible -- always human sign-off"
    (is (= #{:log-extraction-record :schedule-mining-operation}
           (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-extraction-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-radiological-concern} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :coordinate-shipment} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :log-extraction-record} :commit))))
  (is (= :phase-disabled (:reason (phase/gate 0 {:op :log-extraction-record} :commit)))))

(deftest gate-auto-commits-a-clean-auto-eligible-write-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :log-extraction-record} :commit)))))

(deftest verdict->disposition-priority
  (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))
