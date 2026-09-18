import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Calendar, MapPin, ChevronLeft, ChevronRight } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { eventApi } from '../api';
import { EventResponse } from '../types';

import { useLanguage } from '../i18n';

export default function HomePage() {
  const [events, setEvents] = useState<EventResponse[]>([]);

  const [currentSlide, setCurrentSlide] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const navigate = useNavigate();
  const { t } = useLanguage();

  const bannerEvents = events.filter(e => e.hot).slice(0, 5); // Max 5 hot events for the banner
  const eventsToShow = bannerEvents.length > 0 ? bannerEvents : events.slice(0, 2);

  // Auto slide for banner
  useEffect(() => {
    if (!eventsToShow || eventsToShow.length === 0 || isPaused) return;
    const timer = setInterval(() => {
      setCurrentSlide((prev) => {
        if (Number.isNaN(prev)) return 0;
        return (prev + 1) % eventsToShow.length;
      });
    }, 7000);
    return () => clearInterval(timer);
  }, [eventsToShow.length, isPaused]);

  useEffect(() => {
    eventApi.list().then(res => {
      const data = res.data.data || [];
      const activeEvents = data.filter((e: EventResponse) => {
        if (!e.eventDate) return true;
        const dateStr = e.eventDate.replace(' ', 'T');
        return new Date(dateStr).getTime() >= Date.now();
      });
      const sorted = [...activeEvents].sort((a, b) => {
        if (a.hot && !b.hot) return -1;
        if (!a.hot && b.hot) return 1;
        return 0;
      });
      setEvents(sorted);
    }).catch(() => { });
  }, []);

  const formatDate = (d: string) => {
    if (!d) return '';
    const dt = new Date(d);
    const day = dt.getDate().toString().padStart(2, '0');
    const month = (dt.getMonth() + 1).toString().padStart(2, '0');
    const year = dt.getFullYear();
    return `${day}/${month}/${year}`;
  };

  return (
    <div className="bg-bg-primary min-h-screen pb-[60px] overflow-hidden">

      {/* Cinematic Premium Banner - EXPANDED WIDTH CONTENT */}
      <div
        className="relative h-[800px] w-full overflow-hidden shadow-2xl group mb-20"
        onMouseEnter={() => setIsPaused(true)}
        onMouseLeave={() => setIsPaused(false)}
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={currentSlide}
            initial={{ opacity: 0, scale: 1.1 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.8, ease: "easeOut" }}
            className="absolute inset-0"
          >
            <img
              src={eventsToShow[currentSlide]?.bannerUrl || eventsToShow[currentSlide]?.images?.[0] || 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30'}
              alt="Banner"
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-r from-black/90 via-black/40 to-transparent"></div>
            <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent"></div>
          </motion.div>
        </AnimatePresence>

        {/* Content Layer - Expanded Horizontal Space */}
        <div className="absolute inset-0 z-10">
          <div className="container relative mx-auto px-6 max-w-[95%] h-full flex flex-col justify-center">

            {/* Left Content */}
            <motion.div
              key={`content-${currentSlide}`}
              initial={{ opacity: 0, x: -50 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="max-w-4xl"
            >
              <div className="flex items-center gap-2 text-accent-primary mb-6">
                <div className="w-12 h-[2px] bg-accent-primary"></div>
                <span className="font-bold tracking-[0.3em] text-sm uppercase">{eventsToShow[currentSlide]?.category}</span>
              </div>
              <h2 className="text-6xl lg:text-8xl font-black text-white mb-8 uppercase leading-[0.85] tracking-tighter drop-shadow-2xl">
                {eventsToShow[currentSlide]?.name}
              </h2>
              <p className="text-text-secondary text-xl mb-10 line-clamp-3 max-w-2xl leading-relaxed drop-shadow-md">
                {eventsToShow[currentSlide]?.description || t('home.bannerDesc')}
              </p>
              <div className="flex gap-6">
                <button
                  onClick={() => navigate(`/events/${eventsToShow[currentSlide]?.id}`)}
                  className="px-12 py-5 bg-accent-primary text-white font-black rounded-full hover:bg-accent-secondary hover:scale-105 active:scale-95 transition-all shadow-2xl shadow-accent-primary/40 flex items-center gap-3 group/btn text-lg"
                >
                  {t('home.viewDetails')}
                  <ChevronRight size={24} className="group-hover/btn:translate-x-1 transition-transform" />
                </button>
              </div>
            </motion.div>

            {/* Right-Bottom Navigation (Thumbnails + Controls) */}
            <div className="absolute right-0 bottom-8 lg:bottom-12 flex flex-col items-center lg:items-end gap-6 w-full lg:w-auto">

              {/* Thumbnails - Hidden on Mobile/Tablet */}
              <div className="hidden lg:flex gap-4 pointer-events-auto">
                {eventsToShow.map((item, index) => {
                  if (index === currentSlide) return null;
                  return (
                    <div
                      key={item.id}
                      onClick={() => setCurrentSlide(index)}
                      className="w-44 h-60 rounded-2xl overflow-hidden border-2 border-white/10 cursor-pointer shadow-2xl relative group/thumb transition-all"
                    >
                      <img src={item.images?.[0] || item.bannerUrl || 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30'} alt="Thumb" className="w-full h-full object-cover" />
                      <div className="absolute inset-0 bg-black/50 group-hover/thumb:bg-black/0 transition-all duration-300"></div>
                      <div className="absolute bottom-5 left-5 right-5 text-white z-10">
                        <p className="text-[10px] font-bold uppercase opacity-70 mb-1">{item.category}</p>
                        <p className="text-sm font-black line-clamp-2 leading-tight uppercase tracking-tighter">{item.name}</p>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Controls (Arrows + Progress) - Always visible, repositioned for mobile */}
              <div className="flex w-full items-center gap-4 lg:gap-8 bg-black/40 lg:bg-black/20 backdrop-blur-md p-3 lg:p-4 rounded-full lg:rounded-2xl border border-white/10 pointer-events-auto">
                <div className="flex gap-2 lg:gap-3">
                  <button
                    onClick={() => setCurrentSlide(prev => {
                      if (eventsToShow.length === 0) return 0;
                      return (Number.isNaN(prev) ? 0 : prev - 1 + eventsToShow.length) % eventsToShow.length;
                    })}
                    className="w-10 h-10 lg:w-12 lg:h-12 rounded-full border border-white/30 flex items-center justify-center text-white hover:bg-white hover:text-black transition-all active:scale-90"
                  >
                    <ChevronLeft size={20} />
                  </button>
                  <button
                    onClick={() => setCurrentSlide(prev => {
                      if (eventsToShow.length === 0) return 0;
                      return (Number.isNaN(prev) ? 0 : prev + 1) % eventsToShow.length;
                    })}
                    className="w-10 h-10 lg:w-12 lg:h-12 rounded-full border border-white/30 flex items-center justify-center text-white hover:bg-white hover:text-black transition-all active:scale-90"
                  >
                    <ChevronRight size={20} />
                  </button>
                </div>

                <div className="flex w-full items-end gap-1.5 lg:gap-2 pr-2">
                  <span className="text-xl lg:text-3xl font-black text-white leading-none">0{Number.isNaN(currentSlide) || eventsToShow.length === 0 ? 1 : currentSlide + 1}</span>
                  <span className="text-xs lg:text-sm font-bold text-white/40 leading-none mb-0.5 lg:mb-1">/</span>
                  <span className="text-xs lg:text-sm font-bold text-white/40 leading-none">0{Math.max(1, eventsToShow.length)}</span>
                  <div className="w-full h-[2px] lg:h-[3px] bg-white/20 mb-1 lg:mb-1.5 mx-1 relative overflow-hidden rounded-full">
                    <motion.div
                      className="absolute inset-0 bg-accent-primary"
                      initial={{ width: 0 }}
                      animate={{ width: `${(eventsToShow.length === 0) ? 0 : ((currentSlide + 1) / eventsToShow.length) * 100}%` }}
                    />
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>
      </div>

      {/* Categories Content - Centered by Container */}
      <div className="container mx-auto px-6 max-w-7xl">
        {/* Dynamic Category Sections based on Standard Filters */}
        {[
          { id: 'LIVE_MUSIC', label: t('nav.concerts'), match: (c?: string) => ['LIVE_MUSIC', 'CONCERTS', 'POP', 'ROCK', 'WORLD'].includes(c?.toUpperCase() || '') },
          { id: 'ARTS', label: t('nav.arts'), match: (c?: string) => ['ARTS', 'THEATER'].includes(c?.toUpperCase() || '') },
          { id: 'SPORTS', label: t('nav.sports'), match: (c?: string) => ['SPORTS', 'SPORT'].includes(c?.toUpperCase() || '') },
          { id: 'WORKSHOP', label: t('nav.workshop'), match: (c?: string) => ['WORKSHOP', 'SEMINAR'].includes(c?.toUpperCase() || '') },
          { id: 'EXPERIENCE', label: t('nav.experience'), match: (c?: string) => ['EXPERIENCE', 'EXHIBITION'].includes(c?.toUpperCase() || '') },
          { id: 'OTHER', label: t('nav.other'), match: (c?: string) => ['OTHER', 'ENTERTAINMENT'].includes(c?.toUpperCase() || '') },
        ].map(cat => {
          const categoryEvents = events.filter(e => cat.match(e.category)).slice(0, 4);
          if (categoryEvents.length === 0) return null;

          return (
            <div key={cat.id} className="mt-20 first:mt-0">
              <div className="flex justify-between items-end mb-8">
                <div className="flex items-center gap-3">
                  <div className="w-1.5 h-8 bg-accent-primary rounded-full"></div>
                  <h2 className="text-3xl font-black uppercase tracking-tighter text-white">{cat.label}</h2>
                </div>
                <Link to={`/events?category=${cat.id}`} className="text-accent-primary text-sm font-bold hover:underline transition-all">
                  {t('home.seeAll')} &gt;
                </Link>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
                {categoryEvents.map(item => (
                  <Link to={`/events/${item.id}`} key={item.id} className="group bg-bg-card rounded-2xl overflow-hidden border border-border-color hover:border-accent-primary/40 hover:-translate-y-2 hover:shadow-[0_20px_50px_rgba(0,177,79,0.15)] transition-all duration-500">
                    <div className="relative h-[280px] overflow-hidden">
                      <img src={item.images?.[0] || item.bannerUrl || 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30'} alt={item.name} className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" />
                      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center">
                        <span className="px-8 py-3 bg-accent-primary text-white font-black rounded-full shadow-2xl transform translate-y-6 group-hover:translate-y-0 transition-all duration-500">
                          {t('home.buyTicket')}
                        </span>
                      </div>
                      {item.hot && (
                        <div className="absolute top-4 left-4 bg-red-600 text-white text-[10px] font-black px-3 py-1.5 rounded-full flex items-center gap-1 uppercase tracking-widest shadow-lg">
                          <span>🔥</span> HOT
                        </div>
                      )}
                    </div>
                    <div className="p-6">
                      <div className="text-xl font-bold text-white mb-4 line-clamp-1 group-hover:text-accent-primary transition-colors">
                        {item.name}
                      </div>
                      <div className="space-y-3">
                        <div className="text-sm text-text-muted flex items-center gap-2">
                          <MapPin size={16} className="text-accent-primary" />
                          <span className="truncate font-medium">{item.venue}, {item.city}</span>
                        </div>
                        <div className="text-sm text-text-muted flex items-center gap-2">
                          <Calendar size={16} className="text-accent-primary" />
                          <span className="font-medium">{formatDate(item.eventDate)}</span>
                        </div>
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          );
        })}

        {events.length === 0 && (
          <div className="mt-20 text-center py-24 bg-bg-card rounded-3xl border border-dashed border-border-color">
            <div className="text-6xl mb-6">🎫</div>
            <div className="text-text-muted text-xl font-bold">{t('home.noEventsInSystem')}</div>
          </div>
        )}
      </div>
    </div>
  );
}
