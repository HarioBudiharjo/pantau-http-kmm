package com.pantauhttp.internal.capture

import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLock
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject

/** Routes internal-session callbacks back to the owning protocol instance by task identifier. */
internal val internalSessionDelegate: InternalSessionDelegate by lazy { InternalSessionDelegate() }

/** A class rather than an `object`: Kotlin/Native cannot lower singleton objects that subclass ObjC types. */
internal class InternalSessionDelegate : NSObject(), NSURLSessionDataDelegateProtocol {

    private val lock = NSLock()
    private val owners = HashMap<ULong, PantauUrlProtocol>()

    fun register(task: NSURLSessionTask, owner: PantauUrlProtocol) {
        lock.lock()
        owners[task.taskIdentifier] = owner
        lock.unlock()
    }

    private fun owner(task: NSURLSessionTask): PantauUrlProtocol? {
        lock.lock()
        val owner = owners[task.taskIdentifier]
        lock.unlock()
        return owner
    }

    private fun forget(task: NSURLSessionTask) {
        lock.lock()
        owners.remove(task.taskIdentifier)
        lock.unlock()
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        owner(dataTask)?.didReceive(didReceiveResponse)
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        owner(dataTask)?.didReceive(didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        owner(task)?.didComplete(didCompleteWithError)
        forget(task)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        owner(task)?.wasRedirected(newRequest, willPerformHTTPRedirection)
        completionHandler(null)
    }
}
