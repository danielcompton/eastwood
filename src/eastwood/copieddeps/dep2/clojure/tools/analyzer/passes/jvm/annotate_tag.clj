;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns eastwood.copieddeps.dep2.clojure.tools.analyzer.passes.jvm.annotate-tag
  (:require [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.utils :refer [unbox maybe-class]]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :refer [prewalk]])
  (:import (clojure.lang ISeq Var AFunction)))

(defmulti -annotate-tag :op)

(defmethod -annotate-tag :default
  [ast]
  (if (= :const (:op ast))
    (let [t (class (:val ast))]
      (assoc ast :o-tag t :tag t))
    ast))

(defmethod -annotate-tag :map
  [{:keys [val form] :as ast}]
  (let [t (class (or val form))]
    (assoc ast :o-tag t :tag t)))

(defmethod -annotate-tag :set
  [{:keys [val form] :as ast}]
  (let [t (class (or val form))]
    (assoc ast :o-tag t :tag t)))

(defmethod -annotate-tag :vector
  [{:keys [val form] :as ast}]
  (let [t (class (or val form))]
    (assoc ast :o-tag t :tag t)))

(defmethod -annotate-tag :seq
  [ast]
  (assoc ast :o-tag ISeq :tag ISeq))

;; char and numbers are unboxed by default
(defmethod -annotate-tag :number
  [ast]
  (let [t (unbox (class (:val ast)))]
    (assoc ast :o-tag t :tag t)))

(defmethod -annotate-tag :char
  [ast]
  (assoc ast :o-tag Character/TYPE :tag Character/TYPE))

(defmethod -annotate-tag :the-var
  [ast]
  (assoc ast :o-tag Var :tag Var))

(defmethod -annotate-tag :const
  [ast]
  ((get-method -annotate-tag (:type ast)) ast))

(defmethod -annotate-tag :quote
  [ast]
  (let [tag (-> ast :expr -annotate-tag :tag)]
    (assoc ast :tag tag :o-tag tag)))

(defmethod -annotate-tag :binding
  [{:keys [form tag atom o-tag init local name variadic?] :as ast}]
  (let [o-tag (or (:tag init) ;; should defer to infer-tag?
                  (and (= :fn local) AFunction)
                  (and (= :arg local) variadic? ISeq)
                  o-tag
                  Object)
        o-tag (if (#{Void Void/TYPE} o-tag)
                Object
                o-tag)]
    (if-let [tag (or (:tag (meta form)) tag)]
      (let [ast (assoc ast :tag tag :o-tag tag)]
        (if init
          (assoc-in ast [:init :tag] (maybe-class tag))
          ast))
      (assoc ast :tag o-tag :o-tag o-tag))))

(defmethod -annotate-tag :local
  [{:keys [name form tag atom case-test] :as ast}]
  (let [o-tag (@atom :tag)]
    (assoc ast :o-tag o-tag :tag o-tag)))

(defn annotate-tag
  "If the AST node type is a constant object or contains :tag metadata,
   attach the appropriate :tag and :o-tag to the node."
  [{:keys [op tag o-tag atom] :as ast}]
  (let [ast (if (and atom (:case-test @atom))
              (update-in ast [:form] vary-meta dissoc :tag)
              ast)
        ast
        (if (and o-tag tag)
          ast
          (if-let [tag (or tag
                           (-> ast :val meta :tag)
                           (-> ast :form meta :tag))]
            (assoc (-annotate-tag ast) :tag tag)
            (-annotate-tag ast)))]
    (when (= op :binding)
      (swap! atom assoc :tag (:tag ast)))
    ast))
