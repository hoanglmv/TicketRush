import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  X, Send, Sparkles, Calendar, MapPin, 
  Bot, RotateCcw, ArrowUpRight, ThumbsUp, ThumbsDown 
} from 'lucide-react';
import { chatbotApi } from '../api';
import { ChatbotEventCard } from '../types';

interface Message {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  events?: ChatbotEventCard[];
  suggestions?: string[];
  actionType?: string;
  targetEventId?: number;
  logId?: string;
  latencyMs?: number;
  feedback?: 'positive' | 'negative';
  timestamp: string;
}

export default function ChatbotWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [inputMessage, setInputMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // Scroll to bottom whenever messages change
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) {
      scrollToBottom();
      inputRef.current?.focus();
    }
  }, [messages, isOpen]);

  // Load welcome message on first open
  useEffect(() => {
    if (isOpen && messages.length === 0) {
      fetchWelcomeMessage();
    }
  }, [isOpen]);

  const fetchWelcomeMessage = async () => {
    setIsLoading(true);
    try {
      const res = await chatbotApi.getWelcome();
      if (res.data?.data) {
        const d = res.data.data;
        setMessages([
          {
            id: 'welcome',
            sender: 'bot',
            text: d.reply,
            events: d.events,
            suggestions: d.suggestions || [
              'Concert tại TP.HCM 🎤',
              'Sự kiện tại Hà Nội 🏛️',
              'Hàng đợi ảo hoạt động ra sao? 🚦',
              'Thời gian giữ ghế bao lâu? ⏱️'
            ],
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          }
        ]);
      }
    } catch {
      setMessages([
        {
          id: 'welcome-fallback',
          sender: 'bot',
          text: '👋 **Xin chào! Tôi là Trợ lý AI TicketRush.**\n\nBạn đang tìm kiếm sự kiện âm nhạc, thể thao hay cần hỗ trợ đặt vé hôm nay?',
          suggestions: [
            'Concert tại TP.HCM 🎤',
            'Sự kiện tại Hà Nội 🏛️',
            'Hàng đợi ảo hoạt động ra sao? 🚦'
          ],
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputMessage).trim();
    if (!text || isLoading) return;

    const userMsgId = Date.now().toString();
    const newMessages: Message[] = [
      ...messages,
      {
        id: userMsgId,
        sender: 'user',
        text,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ];

    setMessages(newMessages);
    setInputMessage('');
    setIsLoading(true);

    try {
      const res = await chatbotApi.sendMessage(text);
      if (res.data?.data) {
        const d = res.data.data;
        setMessages([
          ...newMessages,
          {
            id: (Date.now() + 1).toString(),
            sender: 'bot',
            text: d.reply,
            events: d.events,
            suggestions: d.suggestions,
            actionType: d.actionType,
            targetEventId: d.targetEventId,
            logId: d.logId,
            latencyMs: d.latencyMs,
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          }
        ]);
      }
    } catch {
      setMessages([
        ...newMessages,
        {
          id: (Date.now() + 1).toString(),
          sender: 'bot',
          text: 'Xin lỗi bạn, kết nối đến Trợ lý AI đang gián đoạn. Bạn vui lòng thử lại sau giây lát hoặc sử dụng thanh tìm kiếm nhé!',
          suggestions: ['Xem tất cả sự kiện', 'Sự kiện Hot'],
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetChat = () => {
    setMessages([]);
    fetchWelcomeMessage();
  };

  const handleFeedback = async (msgId: string, logId: string, isPositive: boolean) => {
    try {
      await chatbotApi.sendFeedback(logId, isPositive);
      setMessages(prev =>
        prev.map(m => (m.id === msgId ? { ...m, feedback: isPositive ? 'positive' : 'negative' } : m))
      );
    } catch (e) {
      console.error('Feedback submission error', e);
    }
  };

  const formatTextWithMarkdown = (text: string) => {
    const lines = text.split('\n');
    return lines.map((line, idx) => {
      // Parse **bold**
      const parts = line.split(/(\*\*.*?\*\*)/g);
      return (
        <p key={idx} className={line.trim() === '' ? 'h-2' : 'my-0.5'}>
          {parts.map((part, pIdx) => {
            if (part.startsWith('**') && part.endsWith('**')) {
              return <strong key={pIdx} className="font-semibold text-gray-900">{part.slice(2, -2)}</strong>;
            }
            return part;
          })}
        </p>
      );
    });
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      {/* Floating Action Button */}
      <AnimatePresence>
        {!isOpen && (
          <motion.button
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setIsOpen(true)}
            className="group relative flex items-center justify-center w-14 h-14 rounded-full bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-500 text-white shadow-xl hover:shadow-indigo-500/30 transition-all duration-300"
            aria-label="Open AI Assistant"
          >
            <span className="absolute -top-1 -right-1 flex h-4 w-4">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-4 w-4 bg-emerald-500 border-2 border-white"></span>
            </span>
            <Bot className="w-7 h-7 transition-transform group-hover:scale-110" />
          </motion.button>
        )}
      </AnimatePresence>

      {/* Chat Window Modal */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, y: 20 }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
            className="w-[90vw] sm:w-[410px] h-[580px] max-h-[85vh] bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-gray-100 flex flex-col overflow-hidden"
          >
            {/* Header */}
            <div className="px-4 py-3.5 bg-gradient-to-r from-indigo-600 via-purple-600 to-indigo-700 text-white flex items-center justify-between shadow-sm">
              <div className="flex items-center gap-3">
                <div className="relative w-10 h-10 rounded-full bg-white/15 border border-white/20 flex items-center justify-center">
                  <Sparkles className="w-5 h-5 text-amber-300" />
                  <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-400 border-2 border-indigo-700 rounded-full"></span>
                </div>
                <div>
                  <h3 className="font-semibold text-sm flex items-center gap-1.5 leading-tight">
                    TicketRush AI Concierge
                  </h3>
                  <p className="text-xs text-indigo-100 font-normal">
                    Trợ lý hỗ trợ đặt vé thông minh
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={handleResetChat}
                  title="Làm mới hội thoại"
                  className="p-1.5 rounded-lg hover:bg-white/20 text-indigo-100 hover:text-white transition-colors"
                >
                  <RotateCcw className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setIsOpen(false)}
                  title="Đóng chat"
                  className="p-1.5 rounded-lg hover:bg-white/20 text-indigo-100 hover:text-white transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Messages Area */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 text-sm scroll-smooth">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}
                >
                  <div
                    className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 ${
                      msg.sender === 'user'
                        ? 'bg-gradient-to-r from-indigo-600 to-purple-600 text-white rounded-br-none shadow-md'
                        : 'bg-gray-100 text-gray-800 rounded-tl-none border border-gray-200/70'
                    }`}
                  >
                    <div className="leading-relaxed">
                      {formatTextWithMarkdown(msg.text)}
                    </div>
                  </div>

                  {/* Interactive Event Cards (if returned by agent) */}
                  {msg.events && msg.events.length > 0 && (
                    <div className="w-full mt-2.5 space-y-2">
                      {msg.events.map((ev) => (
                        <div
                          key={ev.id}
                          onClick={() => {
                            navigate(`/events/${ev.id}`);
                            setIsOpen(false);
                          }}
                          className="group flex gap-3 p-2.5 bg-white rounded-xl border border-gray-200 hover:border-indigo-400 hover:shadow-md transition-all cursor-pointer"
                        >
                          <div className="w-16 h-16 rounded-lg overflow-hidden bg-gray-100 shrink-0">
                            <img
                              src={ev.bannerUrl || 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30'}
                              alt={ev.name}
                              className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                            />
                          </div>
                          <div className="flex-1 min-w-0 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center gap-1.5">
                                {ev.isHot && (
                                  <span className="px-1.5 py-0.5 text-[10px] font-semibold bg-rose-100 text-rose-600 rounded">
                                    HOT
                                  </span>
                                )}
                                <h4 className="font-semibold text-gray-900 text-xs truncate group-hover:text-indigo-600">
                                  {ev.name}
                                </h4>
                              </div>
                              <p className="text-[11px] text-gray-500 flex items-center gap-1 mt-0.5 truncate">
                                <MapPin className="w-3 h-3 shrink-0" /> {ev.venue}, {ev.city}
                              </p>
                              {ev.eventDate && (
                                <p className="text-[11px] text-gray-500 flex items-center gap-1 mt-0.5">
                                  <Calendar className="w-3 h-3 shrink-0" /> {ev.eventDate}
                                </p>
                              )}
                            </div>
                            <div className="flex items-center justify-between mt-1 pt-1 border-t border-gray-100">
                              <span className="text-xs font-bold text-indigo-600">
                                {ev.minPrice ? `${ev.minPrice.toLocaleString()} đ` : 'Xem chi tiết'}
                              </span>
                              <span className="text-[11px] text-indigo-600 font-medium flex items-center gap-0.5 group-hover:translate-x-0.5 transition-transform">
                                Chọn vé <ArrowUpRight className="w-3 h-3" />
                              </span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Suggestion Chips */}
                  {msg.suggestions && msg.suggestions.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-2.5 max-w-[95%]">
                      {msg.suggestions.map((sug, sIdx) => (
                        <button
                          key={sIdx}
                          onClick={() => handleSendMessage(sug)}
                          className="px-2.5 py-1 text-xs bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-full border border-indigo-200/80 transition-all text-left"
                        >
                          {sug}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Footer info: timestamp, latency, feedback */}
                  <div className="flex items-center justify-between w-full mt-1 px-1 text-[10px] text-gray-400">
                    <div className="flex items-center gap-1.5">
                      <span>{msg.timestamp}</span>
                      {msg.latencyMs && (
                        <span className="text-[9px] px-1 bg-gray-100 rounded text-gray-400 font-mono">
                          ⚡{msg.latencyMs}ms
                        </span>
                      )}
                    </div>
                    {msg.sender === 'bot' && msg.logId && (
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleFeedback(msg.id, msg.logId!, true)}
                          className={`p-1 rounded hover:bg-gray-100 transition-colors ${
                            msg.feedback === 'positive' ? 'text-emerald-600 bg-emerald-50 font-bold' : 'text-gray-400 hover:text-emerald-600'
                          }`}
                          title="Hữu ích"
                        >
                          <ThumbsUp className="w-3 h-3" />
                        </button>
                        <button
                          onClick={() => handleFeedback(msg.id, msg.logId!, false)}
                          className={`p-1 rounded hover:bg-gray-100 transition-colors ${
                            msg.feedback === 'negative' ? 'text-rose-600 bg-rose-50 font-bold' : 'text-gray-400 hover:text-rose-600'
                          }`}
                          title="Chưa hữu ích"
                        >
                          <ThumbsDown className="w-3 h-3" />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {/* Typing indicator */}
              {isLoading && (
                <div className="flex items-center gap-2 text-gray-400 text-xs">
                  <div className="w-7 h-7 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center">
                    <Bot className="w-4 h-4" />
                  </div>
                  <div className="bg-gray-100 rounded-full px-3.5 py-2 flex items-center gap-1 border border-gray-200">
                    <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.3s]"></span>
                    <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce [animation-delay:-0.15s]"></span>
                    <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce"></span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Input Box */}
            <div className="p-3 border-t border-gray-100 bg-gray-50/80">
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleSendMessage();
                }}
                className="flex items-center gap-2"
              >
                <input
                  ref={inputRef}
                  type="text"
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  placeholder="Hỏi về sự kiện, giá vé, ghế trống..."
                  className="flex-1 px-3.5 py-2 text-xs bg-white rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-gray-800 placeholder-gray-400"
                  disabled={isLoading}
                />
                <button
                  type="submit"
                  disabled={!inputMessage.trim() || isLoading}
                  className="p-2 rounded-xl bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-all shadow-sm shrink-0"
                >
                  <Send className="w-4 h-4" />
                </button>
              </form>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
