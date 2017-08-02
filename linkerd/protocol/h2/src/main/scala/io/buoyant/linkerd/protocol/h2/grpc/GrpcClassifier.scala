package io.buoyant.linkerd.protocol.h2.grpc

import com.twitter.finagle.buoyant.h2.service.{H2Classifier, H2ReqRep, H2ReqRepFrame}
import com.twitter.finagle.service.ResponseClass
import com.twitter.util.Return
import io.buoyant.grpc.runtime.GrpcStatus
import io.buoyant.grpc.runtime.GrpcStatus.{Ok, Unavailable}


trait GrpcClassifier extends H2Classifier {
  /**
    * @inheritdoc
    * Since GRPC sends status codes in the
    * [[com.twitter.finagle.buoyant.h2.Frame.Trailers Trailers]] frame of an H2
    * stream, we can never attempt early classification
    */
  override val responseClassifier: PartialFunction[H2ReqRep, ResponseClass] =
    PartialFunction.empty
}


/**
  * [[H2Classifier]]s for gRPC
  */
object GrpcClassifiers {

  /**
    * [[H2Classifier]] that classifies all error status
    * codes as [[ResponseClass.RetryableFailure]]
    */
  object AlwaysRetryable extends GrpcClassifier {

    override val streamClassifier: PartialFunction[H2ReqRepFrame, ResponseClass] = {
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(Ok(_))))))) =>
        ResponseClass.Success
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(_)))))) =>
        ResponseClass.RetryableFailure
    }

  }

  /**
    * [[H2Classifier]] that classifies all error status
    * codes as [[ResponseClass.NonRetryableFailure]]
    */
  object NeverRetryable extends GrpcClassifier {

    override val streamClassifier: PartialFunction[H2ReqRepFrame, ResponseClass] = {
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(Ok(_))))))) =>
        ResponseClass.Success
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(_)))))) =>
        ResponseClass.NonRetryableFailure
    }

  }

  /**
    * The default [[H2Classifier]] for gRPC.
    *
    * [[Unavailable]] is marked as retryable, and all other
    * failures are marked as non-retryable.
    */
  object Default extends GrpcClassifier {

    override val streamClassifier: PartialFunction[H2ReqRepFrame, ResponseClass] = {
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(Ok(_))))))) =>
        ResponseClass.Success
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(Unavailable(_))))))) =>
        ResponseClass.RetryableFailure
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(_)))))) =>
        ResponseClass.NonRetryableFailure
    }

  }

  /**
    * a [[GrpcClassifier]] that marks any error in a specified list of codes
    * as [[ResponseClass.RetryableFailure]], and the rest as [[ResponseClass.NonRetryableFailure]]
    * @param retryableCodes a set of status codes which should be marked retryable
    */
  class RetryableStatusCodes(val retryableCodes: Set[Int]) extends GrpcClassifier {
    override val streamClassifier: PartialFunction[H2ReqRepFrame, ResponseClass] = {
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(Ok(_))))))) =>
        ResponseClass.Success
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(status))))))
        if retryableCodes.contains(status.code) =>
          ResponseClass.NonRetryableFailure
      case H2ReqRepFrame(_, Return((_, Some(Return(GrpcStatus(_)))))) =>
        ResponseClass.NonRetryableFailure
    }
  }


}