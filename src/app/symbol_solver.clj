(ns app.symbol_solver
  (:use [app.javaparser])
  (:use [app.operations])
  (:use [app.itemsOnLifecycle])
  (:use [app.utils])
  (:require [instaparse.core :as insta])
  (:import [app.operations Operation]
           [jdk.nashorn.internal.ir Symbol]))

(import com.github.javaparser.JavaParser)
(import com.github.javaparser.ast.CompilationUnit)
(import com.github.javaparser.ast.Node)
(import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration)
(import com.github.javaparser.ast.body.EnumDeclaration)
(import com.github.javaparser.ast.body.EnumConstantDeclaration)
(import com.github.javaparser.ast.body.ConstructorDeclaration)
(import com.github.javaparser.ast.body.FieldDeclaration)
(import com.github.javaparser.ast.body.MethodDeclaration)
(import com.github.javaparser.ast.body.ModifierSet)
(import com.github.javaparser.ast.body.TypeDeclaration)
(import com.github.javaparser.ast.body.VariableDeclaratorId)
(import com.github.javaparser.ast.stmt.ExpressionStmt)
(import com.github.javaparser.ast.stmt.BlockStmt)
(import com.github.javaparser.ast.expr.MethodCallExpr)
(import com.github.javaparser.ast.expr.NameExpr)
(import com.github.javaparser.ast.expr.IntegerLiteralExpr)
(import com.github.javaparser.ast.expr.AssignExpr)
(import com.github.javaparser.ast.expr.VariableDeclarationExpr)
(import com.github.javaparser.ast.body.VariableDeclarator)
(import com.github.javaparser.ast.body.VariableDeclaratorId)
(import com.github.javaparser.ast.visitor.DumpVisitor)

;
; protocol scope
;

(defprotocol scope
  ; for example in a BlockStmt containing statements [a b c d e], when solving symbols in the context of c
  ; it will contains only statements preceeding it [a b]
  (solveSymbol [this context nameToSolve]))

(extend-protocol scope
  NameExpr
  (solveSymbol [this context nameToSolve]
    (if context
      nil
      (solveSymbol (.getParentNode this) nil nameToSolve))))

(extend-protocol scope
  AssignExpr
  (solveSymbol [this context nameToSolve]
    (if context
      (or (solveSymbol (.getTarget this) this nameToSolve) (solveSymbol (.getValue this) this nameToSolve))
      (solveSymbol (.getParentNode this) this nameToSolve))))

(extend-protocol scope
  IntegerLiteralExpr
  (solveSymbol [this context nameToSolve] nil))

(defn find-index [elmts elmt]
  (if (empty? elmts)
    -1
    (if (identical? (first elmts) elmt)
      0
      (let [rest (find-index (rest elmts) elmt)]
        (if (= -1 rest)
          -1
          (+ 1 rest))))))

(defn preceedingChildren [children child]
  (let [i (find-index children child)]
    (if (= -1 i)
      (throw (RuntimeException. "Not found"))
      (take i children))))

(extend-protocol scope
  BlockStmt
  (solveSymbol [this context nameToSolve]
    (let [elementsToConsider (if (nil? context) (.getStmts this) (preceedingChildren (.getStmts this) context))
          solvedSymbols (map (fn [c] (solveSymbol c nil nameToSolve)) elementsToConsider)
          solvedSymbols' (filter (fn [s] (not (nil? s))) solvedSymbols)]
      (first solvedSymbols'))))

(extend-protocol scope
  ExpressionStmt
  (solveSymbol [this context nameToSolve]
    (let [fromExpr (solveSymbol (.getExpression this) this nameToSolve)]
          (or fromExpr (solveSymbol (.getParentNode this) this nameToSolve)))))

(extend-protocol scope
  VariableDeclarationExpr
  (solveSymbol [this context nameToSolve]
    (first (filter (fn [s] (not (nil? (solveSymbol s this nameToSolve)))) (.getVars this)))))

(extend-protocol scope
  VariableDeclarator
  (solveSymbol [this context nameToSolve]
    (solveSymbol (.getId this) nil nameToSolve)))

(extend-protocol scope
  VariableDeclaratorId
  (solveSymbol [this context nameToSolve]
    (if (= nameToSolve (.getName this))
      this
      nil)))

;
; protocol varsymbol
;

(defprotocol varsymbol
  (getType [this]))

(extend-protocol varsymbol
  VariableDeclarator
  (getType [this]
    (let [variableDeclarationExpr (.getParentNode this)]
      (.getType variableDeclarationExpr))))

(defn solveNameExpr [nameExpr]
  ; TODO consider local variables
  ; TODO consider fields
  ; TODO consider inherited fields
  (let [name (.getName nameExpr)]
    (solveSymbol nameExpr nil name)))