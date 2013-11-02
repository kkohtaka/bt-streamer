// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

#ifdef __cplusplus
extern "C" {
#endif

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"

#ifdef __cplusplus
}
#endif

#include <SDL.h>
#include <android/log.h>

#define TAG "MAIN"

int SDL_main(int argc, char* argv[]) {

  // 3.0. Initializes the video subsystem *must be done before anything other!!
  if (SDL_Init(SDL_INIT_VIDEO) < 0) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Unable to init SDL: %s\n", SDL_GetError());
    return -1;
  }

  // prepare variables
  // decoding
  char              *drone_addr = "http://localhost:8888/";
  AVFormatContext   *pFormatCtx = NULL;
  AVCodecContext    *pCodecCtx;
  AVCodec           *pCodec;
  AVPacket          packet;
  AVFrame           *pFrame;
  int               terminate, frameDecoded;

  // converting
  AVFrame           *pFrame_YUV420P;
  uint8_t           *buffer_YUV420P;
  struct SwsContext *pConvertCtx_YUV420P;

  // displaying
  SDL_Window        *pWindow;
  SDL_Renderer      *pRenderer;
  SDL_Texture       *bmpTex;
  uint8_t           *pixels;
  int               pitch, size;

  // SDL event handling
  SDL_Event         event;

  // 1.1 Register all formats and codecs
  av_register_all();
  avcodec_register_all();
  avformat_network_init();

  // 1.2. Open video file
  if (avformat_open_input(&pFormatCtx, drone_addr, NULL, NULL) != 0) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Could not open the video file\n");
    return -1;
  }

  // 1.3. Retrieve stream information
  avformat_find_stream_info(pFormatCtx, NULL);
  // Dump information about file to standard output
  av_dump_format(pFormatCtx, 0, drone_addr, 0);

  // 1.4. Get a pointer to the codec context for the video stream
  // and find the decoder for the video stream
  pCodecCtx = pFormatCtx->streams[0]->codec;
  pCodec = avcodec_find_decoder(pCodecCtx->codec_id);

  // 1.5. Open Codec
  avcodec_open2(pCodecCtx, pCodec, NULL);

  // 2.1.1. Prepare format conversion for diplaying with SDL
  // Allocate an AVFrame structure
  pFrame_YUV420P = avcodec_alloc_frame();
  if (pFrame_YUV420P == NULL) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Could not allocate pFrame_YUV420P\n");
    return -1;
  }
  // Determine required buffer size and allocate buffer
  buffer_YUV420P = (uint8_t *)av_malloc(avpicture_get_size(
      PIX_FMT_YUV420P,
      pCodecCtx->width,
      pCodecCtx->height));
  // Assign buffer to image planes
  avpicture_fill(
      (AVPicture *)pFrame_YUV420P,
      buffer_YUV420P,
      PIX_FMT_YUV420P,
      pCodecCtx->width,
      pCodecCtx->height);
  // format conversion context
  pConvertCtx_YUV420P = sws_getContext(
      pCodecCtx->width,
      pCodecCtx->height,
      pCodecCtx->pix_fmt,
      pCodecCtx->width,
      pCodecCtx->height,
      PIX_FMT_YUV420P,
      SWS_SPLINE,
      NULL,
      NULL,
      NULL);

  // 3.1.1 prepare SDL for YUV
  // allocate window, renderer, texture
  pWindow = SDL_CreateWindow(
      "BTStreamer",
      0,
      0,
      pCodecCtx->width,
      pCodecCtx->height,
      SDL_WINDOW_SHOWN);
  pRenderer = SDL_CreateRenderer(
      pWindow,
      -1,
      SDL_RENDERER_ACCELERATED);
  bmpTex = SDL_CreateTexture(
      pRenderer,
      SDL_PIXELFORMAT_YV12,
      SDL_TEXTUREACCESS_STREAMING,
      pCodecCtx->width,
      pCodecCtx->height);
  size = pCodecCtx->width * pCodecCtx->height;
  if (pWindow == NULL | pRenderer == NULL | bmpTex == NULL) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Could not open window1\n%s\n", SDL_GetError());
    return -1;
  }

  // 1.6. get video frames
  pFrame = avcodec_alloc_frame();
  terminate = 0;
  while (!terminate) {
    // read frame
    if (av_read_frame(pFormatCtx, &packet) < 0) {
      __android_log_print(ANDROID_LOG_INFO, TAG, "Could not read frame!\n");
      continue;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "av_read_frame() done.");

    // decode the frame
    if (avcodec_decode_video2(pCodecCtx, pFrame, &frameDecoded, &packet) < 0) {
      __android_log_print(ANDROID_LOG_INFO, TAG, "Could not decode frame!\n");
      continue;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "avcodec_decode_video2() done.");

    if (frameDecoded) {
      // 2.1.2. convert frame to YUV for Displaying
      sws_scale(
          pConvertCtx_YUV420P,
          (const uint8_t * const *)pFrame->data,
          pFrame->linesize,
          0,
          pCodecCtx->height,
          pFrame_YUV420P->data,
          pFrame_YUV420P->linesize);

      // 3.1.2. copy converted YUV to SDL 2.0 texture
      SDL_LockTexture(bmpTex, NULL, (void **)&pixels, &pitch);
      memcpy(pixels,                pFrame_YUV420P->data[0], size);
      memcpy(pixels + size,         pFrame_YUV420P->data[2], size / 4);
      memcpy(pixels + size * 5 / 4, pFrame_YUV420P->data[1], size / 4);
      SDL_UnlockTexture(bmpTex);
      SDL_UpdateTexture(bmpTex, NULL, pixels, pitch);
      // refresh screen
      SDL_RenderClear(pRenderer);
      SDL_RenderCopy(pRenderer, bmpTex, NULL, NULL);
      SDL_RenderPresent(pRenderer);
    }

    SDL_PollEvent(&event);
    switch (event.type) {
    case SDL_KEYDOWN:
      terminate = 1;
      break;
    case SDL_FINGERDOWN:
      __android_log_print(ANDROID_LOG_INFO, TAG, "SDL_FINGERDOWN");
      break;
    }
  }

  // release
  // *note SDL objects have to be freed before closing avcodec.
  // otherwise it causes segmentation fault for some reason.
  SDL_DestroyTexture(bmpTex);
  SDL_DestroyRenderer(pRenderer);
  SDL_DestroyWindow(pWindow);

  av_free(pFrame_YUV420P);
  av_free(buffer_YUV420P);
  sws_freeContext(pConvertCtx_YUV420P);

  av_free(pFrame);
  avcodec_close(pCodecCtx); // <- before freeing this, all other objects, allocated after this, must be freed
  avformat_close_input(&pFormatCtx);

  SDL_Quit();

  return 0;
}
