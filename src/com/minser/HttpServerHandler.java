package com.minser;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
 
public class HttpServerHandler extends
        SimpleChannelInboundHandler<FullHttpRequest> {
 
private final String url;
     
    public HttpServerHandler(String url){
        this.url = url;
    }
     
    @Override
    protected void messageReceived(ChannelHandlerContext ctx,
            FullHttpRequest request) throws Exception {
        if(!request.getDecoderResult().isSuccess()){
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return ;
        }
        if(request.getMethod() != HttpMethod.GET){
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return ;
        }
         
        final String uri = request.getUri();
        final String path = sanitizeUri(uri);
        if(path == null){
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return ;
        }
        File file = new File(path);
        if(file.isHidden() || !file.exists()){
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return ;
        }
        if(file.isDirectory()){
            if(uri.endsWith("/")){
                sendListing(ctx, file);
            }else{
                sendRedirect(ctx, uri+'/');
            }
            return ;
        }
        if(!file.isFile()){
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
        }
        RandomAccessFile accessFile = null;
        try {
            accessFile = new RandomAccessFile(file, "r");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return ;
        }
        long len = accessFile.length();
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders.setContentLength(response, len);
        setContentTypeHeader(response, file);
        if(HttpHeaders.isKeepAlive(request)){
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ctx.write(response);
        ChannelFuture future;
        future = ctx.write(new ChunkedFile(accessFile, 0, len, 8192), ctx.newProgressivePromise());
        future.addListener(new ChannelProgressiveFutureListener() {
             
            @Override
            public void operationComplete(ChannelProgressiveFuture arg0)
                    throws Exception {
                System.out.println("Transfer complete.");
            }
             
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress,
                    long total) throws Exception {
                if(total < 0){
                    System.err.println("Transfer progress:" + progress);
                }else{
                    System.err.println("Transfer progress:" + progress +"/" +total);
                }
            }
        });
        ChannelFuture lastfuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if(!HttpHeaders.isKeepAlive(request)){
            lastfuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
     
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        if(ctx.channel().isActive()){
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }
     
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private String sanitizeUri(String uri){
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
            System.out.println("ouyang=="+uri);
        } catch (Exception e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (Exception e2) {
                throw new Error();
            }
        }
         
        if(!uri.startsWith(url)){
            return null;
        }
         
        if(!uri.startsWith("/")){
            return null;
        }
         
        uri = uri.replace('/', File.separatorChar);
        if(uri.contains('.'+File.separator) || uri.startsWith(".")
                || uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()){   
            return null;
        }
        
        System.out.println(System.getProperty("user.dir") + File.separator + uri);
        return System.getProperty("user.dir") + File.separator + uri;
    }
     
    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
     
    private static void sendListing(ChannelHandlerContext ctx, File dir){
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
        StringBuilder buf = new StringBuilder();
        String dirPath = dir.getPath();
        buf.append("<!DOCTYPE html\r\n");
        buf.append("<html><head><title>");
        buf.append(dirPath);
        buf.append(" Ŀ¼: ");
        buf.append("</title></head><body>\r\n");
        buf.append("<h3>");
        buf.append(dirPath).append(" Ŀ¼ :");
        buf.append("</h3>");
        buf.append("<ul>");
        buf.append("<li>����: <a href=\"../\">..</a></li>\r\n");
        for(File f :dir.listFiles()){
            if(f.isHidden() || !f.canRead()){
                continue;
            }
            String name = f.getName();
            if(!ALLOWED_FILE_NAME.matcher(name).matches()){
                continue;
            }
            buf.append("<li>����:<a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }
        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        System.out.println("write ended");
    }
     
    private static void sendRedirect(ChannelHandlerContext ctx, String newuri){
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newuri);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
     
    private static void sendError(ChannelHandlerContext ctx, 
            HttpResponseStatus status){
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure: " + status.toString()+"\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE); //�첽���� ������ɺ�͹ر�����
    }
     
    private static void setContentTypeHeader(HttpResponse response, File file){
        MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, typeMap.getContentType(file.getPath()));
    }
     
     
}