package com.minser;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.stream.ChunkedWriteHandler;
 
public class HttpServer {
 
    private static int port = 7777;
    private static final String Default_URL="/js";
     
    public void run(final int port,final String url){
        EventLoopGroup bgroup = new NioEventLoopGroup();
        EventLoopGroup wgroup = new NioEventLoopGroup();
        try{
            ServerBootstrap bootServer = new ServerBootstrap();
            bootServer.group(bgroup, wgroup)
            .channel(NioServerSocketChannel.class).childHandler(
                    new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch)
                                throws Exception {
                            ch.pipeline().addLast("http-decoder", new HttpRequestDecoder());
                            ch.pipeline().addLast("http-aggregator",new HttpObjectAggregator(65536));
                            ch.pipeline().addLast("http-encoder",new HttpRequestDecoder());
                            ch.pipeline().addLast("http-chunked",new ChunkedWriteHandler());
                            ch.pipeline().addLast("fileServerHandler",new HttpServerHandler(url));
                        }
                    });
            ChannelFuture f = bootServer.bind(port).sync();
            System.out.println("HTTP File Server Start on:"+port);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }finally{
            bgroup.shutdownGracefully();
            wgroup.shutdownGracefully();
        }
    }
     
    public static void main(String[] args) {
         
        new HttpServer().run(port,Default_URL);
    }
 
}