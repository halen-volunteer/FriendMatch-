export function registerGlobalErrorHandler(app) {
  app.config.errorHandler = (error, instance, info) => {
    console.error('[VueError]', info, error)
  }

  window.addEventListener('unhandledrejection', (event) => {
    console.error('[UnhandledPromiseRejection]', event.reason)
  })
}
