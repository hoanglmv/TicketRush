import { useState, useEffect, useRef } from 'react';
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
              return <strong key={pIdx} className="font-bold text-white text-[#00b14f]">{part.slice(2, -2)}</strong>;
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
            whileHover={{ scale: 1.08 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setIsOpen(true)}
            className="group relative flex items-center justify-center w-14 h-14 rounded-full bg-gradient-to-tr from-[#008a3d] via-[#00b14f] to-[#00d860] text-white shadow-2xl shadow-[#00b14f]/40 border border-white/20 hover:shadow-[#00b14f]/60 transition-all duration-300"
            aria-label="Open AI Assistant"
          >
            <span className="absolute -top-1 -right-1 flex h-4 w-4">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-4 w-4 bg-emerald-500 border-2 border-[#121216]"></span>
            </span>
            <Bot className="w-7 h-7 transition-transform group-hover:rotate-12 duration-300" />
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
            className="w-[92vw] sm:w-[420px] h-[590px] max-h-[85vh] bg-[#141419]/95 backdrop-blur-2xl rounded-3xl shadow-[0_25px_60px_rgba(0,0,0,0.85)] border border-white/10 flex flex-col overflow-hidden text-white"
          >
            {/* Header */}
            <div className="px-5 py-4 bg-[#1a1a22]/90 border-b border-white/10 text-white flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="relative w-10 h-10 rounded-2xl bg-gradient-to-br from-[#00b14f]/30 to-[#008a3d]/10 border border-[#00b14f]/40 flex items-center justify-center shadow-lg shadow-[#00b14f]/20">
                  <Sparkles className="w-5 h-5 text-[#00b14f]" />
                  <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-[#00b14f] border-2 border-[#1a1a22] rounded-full"></span>
                </div>
                <div>
                  <h3 className="font-black text-sm flex items-center gap-1.5 leading-tight tracking-tight text-white">
                    Ticket<span className="text-[#00b14f]">Rush</span> AI Concierge
                  </h3>
                  <p className="text-[11px] text-white/50 font-medium flex items-center gap-1 mt-0.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-[#00b14f] animate-pulse"></span>
                    Trợ lý hỗ trợ đặt vé thông minh
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={handleResetChat}
                  title="Làm mới hội thoại"
                  className="p-2 rounded-xl hover:bg-white/10 text-white/60 hover:text-white transition-colors"
                >
                  <RotateCcw className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setIsOpen(false)}
                  title="Đóng chat"
                  className="p-2 rounded-xl hover:bg-white/10 text-white/60 hover:text-white transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Messages Area */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 text-sm scroll-smooth bg-gradient-to-b from-[#141419] to-[#0f0f13]">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}
                >
                  <div
                    className={`max-w-[85%] rounded-2xl px-4 py-3 ${
                      msg.sender === 'user'
                        ? 'bg-gradient-to-r from-[#00b14f] to-[#008a3d] text-white font-medium rounded-br-none shadow-lg shadow-[#00b14f]/25'
                        : 'bg-[#1c1c24] text-white/90 rounded-tl-none border border-white/10 shadow-md'
                    }`}
                  >
                    <div className="leading-relaxed text-xs sm:text-sm">
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
                          className="group flex gap-3 p-2.5 bg-[#1a1a22] hover:bg-[#22222c] rounded-2xl border border-white/10 hover:border-[#00b14f]/50 hover:shadow-[0_10px_30px_rgba(0,177,79,0.15)] transition-all cursor-pointer"
                        >
                          <div className="w-16 h-16 rounded-xl overflow-hidden bg-black/40 shrink-0 border border-white/5">
                            <img
                              src={ev.bannerUrl || 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30'}
                              alt={ev.name}
                              className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
                            />
                          </div>
                          <div className="flex-1 min-w-0 flex flex-col justify-between">
                            <div>
                              <div className="flex items-center gap-1.5">
                                {ev.isHot && (
                                  <span className="px-1.5 py-0.5 text-[9px] font-black bg-red-500/20 text-red-400 border border-red-500/30 rounded-full uppercase tracking-wider">
                                    HOT
                                  </span>
                                )}
                                <h4 className="font-bold text-white text-xs truncate group-hover:text-[#00b14f] transition-colors">
                                  {ev.name}
                                </h4>
                              </div>
                              <p className="text-[11px] text-white/50 flex items-center gap-1 mt-1 truncate">
                                <MapPin className="w-3 h-3 text-[#00b14f] shrink-0" /> {ev.venue}, {ev.city}
                              </p>
                              {ev.eventDate && (
                                <p className="text-[11px] text-white/50 flex items-center gap-1 mt-0.5">
                                  <Calendar className="w-3 h-3 text-[#00b14f] shrink-0" /> {ev.eventDate}
                                </p>
                              )}
                            </div>
                            <div className="flex items-center justify-between mt-1 pt-1.5 border-t border-white/5">
                              <span className="text-xs font-black text-[#00b14f]">
                                {ev.minPrice ? `${ev.minPrice.toLocaleString()} đ` : 'Xem chi tiết'}
                              </span>
                              <span className="text-[11px] text-[#00b14f] font-bold flex items-center gap-0.5 group-hover:translate-x-1 transition-transform">
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
                          className="px-3 py-1.5 text-xs bg-white/5 hover:bg-[#00b14f]/15 text-white/80 hover:text-white rounded-full border border-white/10 hover:border-[#00b14f]/50 transition-all text-left backdrop-blur-sm"
                        >
                          {sug}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Footer info: timestamp, latency, feedback */}
                  <div className="flex items-center justify-between w-full mt-1.5 px-1 text-[10px] text-white/40">
                    <div className="flex items-center gap-1.5">
                      <span>{msg.timestamp}</span>
                      {msg.latencyMs && (
                        <span className="text-[9px] px-1.5 py-0.5 bg-white/5 border border-white/10 rounded-md text-white/60 font-mono">
                          ⚡{msg.latencyMs}ms
                        </span>
                      )}
                    </div>
                    {msg.sender === 'bot' && msg.logId && (
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleFeedback(msg.id, msg.logId!, true)}
                          className={`p-1.5 rounded-lg hover:bg-white/10 transition-colors ${
                            msg.feedback === 'positive' ? 'text-[#00b14f] bg-[#00b14f]/20 font-bold' : 'text-white/40 hover:text-[#00b14f]'
                          }`}
                          title="Hữu ích"
                        >
                          <ThumbsUp className="w-3 h-3" />
                        </button>
                        <button
                          onClick={() => handleFeedback(msg.id, msg.logId!, false)}
                          className={`p-1.5 rounded-lg hover:bg-white/10 transition-colors ${
                            msg.feedback === 'negative' ? 'text-rose-400 bg-rose-500/20 font-bold' : 'text-white/40 hover:text-rose-400'
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
                <div className="flex items-center gap-2 text-white/50 text-xs">
                  <div className="w-7 h-7 rounded-full bg-[#00b14f]/20 border border-[#00b14f]/30 text-[#00b14f] flex items-center justify-center">
                    <Bot className="w-4 h-4" />
                  </div>
                  <div className="bg-[#1c1c24] rounded-full px-4 py-2 flex items-center gap-1.5 border border-white/10">
                    <span className="w-1.5 h-1.5 bg-[#00b14f] rounded-full animate-bounce [animation-delay:-0.3s]"></span>
                    <span className="w-1.5 h-1.5 bg-[#00b14f] rounded-full animate-bounce [animation-delay:-0.15s]"></span>
                    <span className="w-1.5 h-1.5 bg-[#00b14f] rounded-full animate-bounce"></span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Input Box */}
            <div className="p-3.5 border-t border-white/10 bg-[#16161c]/95">
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
                  className="flex-1 px-4 py-2.5 text-xs bg-[#202028] hover:bg-[#252532] focus:bg-[#202028] rounded-2xl border border-white/10 focus:outline-none focus:border-[#00b14f] focus:ring-1 focus:ring-[#00b14f] text-white placeholder-white/40 transition-all"
                  disabled={isLoading}
                />
                <button
                  type="submit"
                  disabled={!inputMessage.trim() || isLoading}
                  className="p-2.5 rounded-2xl bg-[#00b14f] text-white hover:bg-[#008a3d] disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-lg shadow-[#00b14f]/25 shrink-0"
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
