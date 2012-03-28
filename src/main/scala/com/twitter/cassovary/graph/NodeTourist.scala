/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.twitter.cassovary.graph

import scala.collection.mutable
import it.unimi.dsi.fastutil.ints.{Int2IntOpenHashMap, Int2ObjectOpenHashMap}

/**
 * Represents the processing done when visiting a node.
 */
trait NodeTourist[+O] {

  // TODO figure out a way to iterate through all key value pairs

  /**
   * Visit a node by its {@code id}
   */
  def visit(id: Int)

  /**
   * Visit a {@code node}
   */
  def visit(node: Node) { visit(node.id) }
}

// Convenience class
class EmptyNodeTourist[O] extends NodeTourist[O] {
  def visit(id: Int) { }
}

object EmptyNodeTourist {
  val instance = new EmptyNodeTourist[Any]
}

/**
 * An InfoKeeper keeps caller-supplied info per node.
 * It provides another method recordInfo() to record a given info for a node.
 */
trait InfoKeeper[O] extends NodeTourist[O] {
  /**
   * Keep info only the first time a node is seen
   */
  val onlyOnce: Boolean

  protected val infoPerNode = new Int2ObjectOpenHashMap[O]

  /**
   * Record information {@code info} of node {@code id}.
   */
  def recordInfo(id: Int, info: O) {
    if (!(onlyOnce && infoPerNode.containsKey(id))) {
      infoPerNode.put(id, info)
    }
  }

  /**
   * Get information of a particular node by its {@code id}
   */
  def infoOfNode(id: Int): Option[O] = {
    if (infoPerNode.containsKey(id)) {
      Some(infoPerNode.get(id))
    } else {
      None
    }
  }

  /**
   * Get information of a particular {@code node}.
   */
  def infoOfNode(node: Node): Option[O] = infoOfNode(node.id)

  /**
   * Clear underlying map
   */
  def clear() {
    infoPerNode.clear()
  }
}

/**
 * A tourist that keeps counts of the number of times a node has been seen.
 */
class VisitsCounter extends NodeTourist[Int] {
  protected val infoPerNode = new Int2IntOpenHashMap

  def visit(id: Int) {
    infoPerNode.add(id, 1)
  }

  def sortedByVisits = {
    val sortedPairs = new Array[(Int, Int)](infoPerNode.size)
    val nodeIterator = infoPerNode.keySet.iterator

    var index = 0
    while (nodeIterator.hasNext) {
      val node = nodeIterator.nextInt
      sortedPairs(index) = (node, infoPerNode.get(node))
      index += 1
    }

    sortedPairs.toList.sortBy {
      case (nodeId, count) => (-count, nodeId)
    }
  }
}

/**
 * A tourist that keeps track of the paths ending at each node. It keeps
 * at {@code numTopPathsPerNode} paths per node.
 *
 * TODO: instead of homeNodeIds, this func should take
 * a function param of Node => Boolean
 */
class PathsCounter(numTopPathsPerNode: Int, homeNodeIds: Seq[Int])
    extends NodeTourist[List[(DirectedPath, Int)]] {
  private val paths = new DirectedPathCollection

  def visit(id: Int) {
    if (homeNodeIds.contains(id)) {
      paths.resetCurrentPath()
    }
    paths.appendToCurrentPath(id)
  }

  val topPathsPerNode = paths.topPathsPerNodeId(numTopPathsPerNode)
}

// collection.Map[Int, List[(DirectedPath, Int)]]

/**
 * A NodeTourist that keeps track of the previous immediate neighbor of a
 * given node in visiting sequence.
 */
class PrevNbrCounter(numTopPathsPerNode: Option[Int], onlyOnce: Boolean)
    extends NodeTourist[List[(Int, Int)]] {
  /**
   * Keep info only the first time a node is seen
   */
  val infoPerNode = new mutable.HashMap[Int, mutable.HashMap[Int, Int]]() {
    override def default(key: Int) = new mutable.HashMap[Int, Int]() {
      override def default(key: Int) = 0
    }
  }

  /**
   * Record the previous neighbor {@code nodeId} of {@code id}.
   */
  def recordInfo(id: Int, nodeId: Int) {
    if (!(onlyOnce && infoPerNode.contains(id))) {
      val infoMap = infoPerNode(id)
      infoMap.put(nodeId, infoMap(nodeId) + 1)
      if (!infoPerNode.contains(id)) { infoPerNode.put(id, infoMap) }
    }
  }

  def visit(id: Int) {}

  private def topPrevNbrsTill(nodeId: Int, num: Option[Int]) = {
    val sorted = infoPerNode(nodeId).toList.sortBy { case (id, count) => -count }
    num match {
      case Some(n) => sorted.take(n)
      case None => sorted
    }
  }

  def infoAllNodes = {
    Map.empty ++ infoPerNode.keysIterator.map { nodeId =>
        (nodeId, topPrevNbrsTill(nodeId, numTopPathsPerNode))
    }
  }
}
