/**
 * Settings page — Telegram-aligned design.
 * Colored icon badges (28px, 9px radius), 16sp labels, indented dividers.
 */

import { useState, useRef, useEffect } from 'react'
import {
  Cookie,
  ChevronRight,
  X,
  RefreshCw,
  Globe,
  Pencil,
  Trash2,
  Check,
  Plus,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { platform } from '@/platform'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { usePreferencesStore, defaultPreferences } from '@/stores/preferences'
import { useProxyStore } from '@/stores/proxy'
import { useBookmarksStore } from '@/stores/bookmarks'
import { TonProxy } from '@/plugins/ton-proxy'
import { APP_NAME, APP_VERSION } from '@shared/constants'
import { BottomSheet } from '@/components/mobile/BottomSheet'

function AnonIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 28 28" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M22.9731 13.3781L24.9061 15.8691C24.9367 15.9087 24.9612 15.9523 24.9775 16.0001C25.0529 16.2289 24.9326 16.4763 24.7083 16.5533C24.0436 16.7841 23.4237 17.0793 22.8507 17.4411C22.2737 17.805 21.7904 18.2063 21.403 18.643C21.3907 18.6555 21.3785 18.67 21.3683 18.6867C21.2337 18.8842 21.2806 19.1566 21.4743 19.2938L22.1126 19.7471C22.1921 19.8033 22.2492 19.8844 22.2777 19.978C22.3471 20.2088 22.2186 20.452 21.9923 20.5227C20.2489 21.0634 18.5911 21.7537 17.0189 22.5938C15.663 23.3195 14.4558 24.0909 13.3996 24.9102C13.2854 24.9996 13.1345 25.0246 12.9999 24.9746C12.7756 24.8956 12.6594 24.6461 12.7369 24.4195L12.7919 24.2593C13.3221 22.783 14.1112 21.4522 15.1613 20.2711C16.5622 18.6971 18.3036 17.516 20.3855 16.7279V14.2618C20.3855 14.1474 20.4711 14.0518 20.5812 14.0435C20.9768 14.0102 21.3479 13.9353 21.6946 13.8168C22.0392 13.7004 22.3715 13.5382 22.6896 13.3302C22.7814 13.2699 22.9037 13.2907 22.9731 13.3781ZM5.60621 13.2991L7.18242 13.7898C7.53314 15.3992 8.1102 16.8069 8.9136 18.0109C9.65787 19.1275 10.6958 20.215 12.0252 21.2796C12.2067 21.4231 12.2434 21.6872 12.1109 21.8764L11.6745 22.5002C11.5522 22.6749 11.3238 22.7331 11.1362 22.6375L6.01403 20.0424C5.94878 20.0091 5.89168 19.9592 5.8509 19.8969C5.71836 19.6972 5.7673 19.4269 5.96305 19.2897L6.42796 18.9674C6.4769 18.932 6.51972 18.8884 6.55031 18.8364C6.67469 18.6305 6.61352 18.3623 6.41165 18.2355L3.20213 16.2081C3.17563 16.1914 3.15116 16.1706 3.12669 16.1478C2.95948 15.9793 2.95744 15.7028 3.12261 15.5302L5.178 13.4093C5.28811 13.2928 5.45532 13.2512 5.60621 13.2991ZM19.0478 14.0643C19.1804 14.293 19.2558 14.5488 19.2558 14.817C19.2558 15.8172 18.2281 16.6281 16.9598 16.6281C15.6915 16.6281 14.6618 15.8172 14.6618 14.817C14.6618 14.6839 14.6801 14.5529 14.7168 14.4261C16.2421 14.3949 17.6817 14.2722 18.9704 14.0747L19.0478 14.0643ZM8.94623 14.0788C10.2594 14.2784 11.7296 14.4011 13.2854 14.4281L13.2976 14.4718C13.3241 14.5841 13.3404 14.6985 13.3404 14.817C13.3404 15.8172 12.3107 16.6281 11.0424 16.6281C9.77409 16.6281 8.7464 15.8172 8.7464 14.817C8.7464 14.555 8.81776 14.3055 8.94623 14.0788ZM16.6662 3C17.8815 3 18.6645 4.92548 19.0173 8.77853C21.2969 9.24015 22.7916 10.0074 22.7916 10.8787C22.7916 12.2906 18.8643 13.4342 14.0215 13.4342C9.17868 13.4342 5.25141 12.2906 5.25141 10.8787C5.25141 10.0074 6.74606 9.23807 9.02983 8.77853C9.45396 4.92548 10.2431 3 11.3952 3C13.1529 3 12.4779 5.21867 13.9624 5.21867C15.4468 5.21867 14.8147 3 16.6662 3Z" fill="currentColor" />
    </svg>
  )
}

function WifiIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 16" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M21.6452 4.13155C18.8113 1.56254 15.085 0 11 0C6.91501 0 3.18874 1.56254 0.354804 4.13155C-0.120507 4.56355 -0.11154 5.31265 0.341353 5.76763L1.84352 7.27962C4.17075 4.84389 7.41274 3.33649 11 3.33649C14.5873 3.33649 17.8292 4.84389 20.1565 7.27962L21.6586 5.76763C22.1115 5.31265 22.1205 4.56355 21.6452 4.13155ZM18.2463 9.19604C16.4033 7.27043 13.8384 6.07554 11 6.07554C8.16158 6.07554 5.59669 7.27043 3.75373 9.19604L5.94645 11.3928C7.22889 10.0508 9.01804 9.21902 11 9.21902C12.982 9.21902 14.7711 10.0508 16.0536 11.3928L18.2463 9.19604ZM11.8026 15.6622L14.1433 13.3138C13.3452 12.4774 12.2331 11.9581 11 11.9581C9.76688 11.9581 8.65483 12.4774 7.85666 13.3138L10.1974 15.6622C10.6413 16.1126 11.3587 16.1126 11.8026 15.6622Z" fill="currentColor" />
    </svg>
  )
}

function FlameIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 28 28" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M12.64 24.9228C9.50685 24.6945 6.7511 22.874 5.36441 19.0546C4.51333 16.7104 5.15302 13.517 7.28352 9.47427C7.47475 9.11126 8.58995 9.53182 8.65381 9.63267L10.324 12.2883C10.4392 12.47 10.9441 12.3112 10.9736 12.2514C11.4554 11.2759 11.856 10.0183 12.1755 8.47867C12.4843 6.9906 12.6347 5.61613 12.64 4.00916C12.6414 3.59465 13.399 2.94855 13.5 3.00326C15.9366 4.32248 17.9506 5.95774 19.1561 7.57153C21.2201 10.3345 21.7615 12.4706 21.9187 15.0658C22.2754 17.8461 21.4583 20.2723 19.4675 22.3444C17.4767 24.4166 15.2009 25.276 12.64 24.9228Z" fill="currentColor" />
    </svg>
  )
}

function TrashIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 18 22" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M7.75981 0H10.2402C10.6794 0 11.0541 2.32346e-08 11.3598 0.0243627C11.6785 0.0487255 11.9928 0.101513 12.29 0.243631C12.7378 0.458841 13.1038 0.803986 13.3321 1.22628C13.4828 1.50646 13.5388 1.80288 13.5646 2.10336C13.5904 2.39166 13.5904 2.74492 13.5904 3.1591V3.63824H17.2679C17.6727 3.63824 18 3.94684 18 4.32853C18 4.71023 17.6727 5.01882 17.2679 5.01882H0.732057C0.327272 5.01882 0 4.71023 0 4.32853C0 3.94684 0.327272 3.63824 0.732057 3.63824H4.40957V3.1591C4.40957 2.74492 4.40957 2.39166 4.43541 2.10336C4.46124 1.80288 4.51722 1.50646 4.66794 1.22628C4.89617 0.803986 5.2622 0.458841 5.71005 0.243631C6.00718 0.101513 6.32153 0.0487255 6.64019 0.0243627C6.94593 2.32346e-08 7.32057 0 7.75981 0ZM12.1263 3.18346V3.63824H5.87368V3.18346C5.87368 2.7368 5.87368 2.44444 5.89521 2.21705C5.91244 2.00185 5.94689 1.90845 5.97703 1.85567C6.06316 1.69324 6.20526 1.55925 6.37751 1.47804C6.43349 1.44961 6.53254 1.41713 6.76077 1.40089C7.00191 1.38058 7.31196 1.38058 7.78565 1.38058H10.2144C10.688 1.38058 10.9981 1.38058 11.2392 1.40089C11.4675 1.41713 11.5665 1.44961 11.6225 1.47804C11.7947 1.55925 11.9368 1.69324 12.023 1.85567C12.0531 1.90845 12.0876 2.00185 12.1048 2.21705C12.1263 2.44444 12.1263 2.7368 12.1263 3.18346ZM2.05837 17.3426L1.40813 8.18199C1.36507 7.56478 1.34354 7.25618 1.4555 7.02067C1.55885 6.80952 1.72679 6.63898 1.9421 6.52935C2.18325 6.40753 2.51483 6.40753 3.16938 6.40753H14.8306C15.4852 6.40753 15.8167 6.40753 16.0579 6.52935C16.2732 6.63898 16.4411 6.80952 16.5445 7.02067C16.6565 7.25618 16.6349 7.56478 16.5919 8.18199L15.9416 17.3426C15.8297 18.983 15.7694 19.8032 15.3947 20.4245C15.0632 20.9727 14.5636 21.4112 13.9608 21.6873C13.2804 22 12.4062 22 10.6622 22H7.3378C5.59378 22 4.71962 22 4.03923 21.6873C3.43636 21.4112 2.93684 20.9727 2.60526 20.4245C2.23062 19.8032 2.17033 18.983 2.05837 17.3426ZM5.97273 9.49354C5.9555 9.11185 5.611 8.81949 5.21053 8.83573C4.80574 8.85197 4.49139 9.17682 4.50861 9.55851L4.96077 18.914C4.97799 19.2957 5.32249 19.588 5.72727 19.5718C6.13206 19.5556 6.4421 19.2307 6.42488 18.849L5.97273 9.49354ZM12.7895 8.83573C13.1943 8.85197 13.5086 9.17682 13.4914 9.55851L13.0392 18.914C13.022 19.2957 12.6775 19.588 12.2727 19.5718C11.8679 19.5556 11.5579 19.2307 11.5751 18.849L12.0273 9.49354C12.0445 9.11185 12.389 8.81949 12.7895 8.83573ZM9 8.83573C9.40479 8.83573 9.73205 9.14433 9.73205 9.52602V18.8815C9.73205 19.2632 9.40479 19.5718 9 19.5718C8.59521 19.5718 8.26794 19.2632 8.26794 18.8815V9.52602C8.26794 9.14433 8.59521 8.83573 9 8.83573Z" fill="currentColor" />
    </svg>
  )
}

function HomeIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 19" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M10.9996 2.58541L18.4349 9.88078V18.0881C18.4349 18.5903 18.0174 19 17.5055 19H14.2525C13.9948 19 13.7878 18.797 13.7878 18.544V13.0725C13.7878 12.8196 13.5809 12.6165 13.3231 12.6165H8.67611C8.41834 12.6165 8.21141 12.8196 8.21141 13.0725V18.544C8.21141 18.797 8.00447 19 7.7467 19H4.49378C3.98189 19 3.56438 18.5903 3.56438 18.0881V9.88078L10.9996 2.58541ZM11.468 0.155991L11.5478 0.223672L16.5761 5.15375V2.12945C16.5761 1.87653 16.783 1.67349 17.0408 1.67349H17.9702C18.2279 1.67349 18.4349 1.87653 18.4349 2.12945V6.97759L21.7713 10.2548C22.0762 10.5505 22.0762 11.0349 21.7713 11.3306C21.4954 11.6049 21.0597 11.6263 20.7547 11.3983L20.6749 11.3306L10.9996 1.83735L1.32437 11.3306C1.04845 11.6049 0.612792 11.6263 0.307831 11.3983L0.22796 11.3306C-0.0515871 11.0599 -0.0733696 10.6324 0.158982 10.3332L0.22796 10.2548L10.4514 0.223672C10.7273 -0.050617 11.163 -0.0719892 11.468 0.155991Z" fill="currentColor" />
    </svg>
  )
}

function JsIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 22" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M13.6122 0.291398C13.4937 0.471363 13.4455 0.698733 13.4939 0.850354C13.5399 0.994549 14.1959 1.50808 14.9986 2.0283C15.7813 2.53552 16.4216 2.97912 16.4216 3.01415C16.4216 3.04905 15.8753 3.50367 15.2074 4.02413C13.4832 5.36818 13.2897 5.6453 13.7531 6.10598C14.1726 6.52285 14.5542 6.34635 16.4839 4.84264C19.0436 2.84792 19.0446 3.00796 16.4608 1.29371C14.3407 -0.11272 13.9657 -0.244661 13.6122 0.291398ZM3.46088 0.483121C3.15815 0.687346 2.26591 1.3697 1.47827 1.99934C0.24308 2.98679 0.0408468 3.19943 0.00722436 3.54587C-0.0308811 3.93873 0.0113336 3.97549 1.96953 5.24775C3.14383 6.01068 4.09783 6.54798 4.27828 6.54798C4.66369 6.54798 5.02034 6.04967 4.90141 5.67724C4.85235 5.52376 4.22049 5.02335 3.39114 4.48122C2.60674 3.96868 1.98161 3.50565 2.00191 3.45242C2.02208 3.39908 2.59902 2.9254 3.28392 2.39962C4.65634 1.34619 4.84052 1.16053 4.84052 0.830798C4.84052 0.570009 4.41762 0.111804 4.17691 0.111804C4.086 0.111804 3.76373 0.278897 3.46088 0.483121ZM8.10875 3.64786C7.47478 4.02549 7.20605 4.49224 7.20816 5.21123C7.20903 5.53601 7.71636 7.35831 8.50923 9.88587C9.22402 12.1642 9.7961 14.0409 9.78065 14.0564C9.76509 14.0717 9.52973 14.0331 9.25776 13.9705C8.57498 13.8131 7.55161 13.823 6.87019 13.9935C5.71308 14.2831 4.83354 15.5328 5.10327 16.5037C5.274 17.1183 5.7883 17.4809 7.03831 17.8683C8.51583 18.3262 9.13411 18.6542 10.306 19.6024C12.4712 21.354 13.6069 21.8897 15.3631 21.9875C17.2893 22.0947 18.9392 21.512 20.1741 20.288C21.7693 18.7069 22.366 16.3817 21.7784 14.0362C21.3224 12.2158 20.1776 8.8348 19.8975 8.48093C19.3663 7.80959 18.3059 7.57888 17.5463 7.96926C17.2306 8.13152 17.1379 8.13152 16.7471 7.96926C16.1506 7.72159 15.3184 7.73892 14.872 8.00837C14.5191 8.22138 14.4888 8.22101 14.0625 8.00107C13.6227 7.77407 12.7516 7.75142 12.3006 7.95515C12.1353 8.02978 12.0118 7.743 11.5605 6.2378C11.2625 5.24391 10.9242 4.27601 10.8085 4.08701C10.3342 3.31182 9.02926 3.09955 8.10875 3.64786ZM8.77198 4.88583C8.66439 4.9929 8.57635 5.14266 8.57635 5.21891C8.57635 5.29503 9.27682 7.59967 10.1329 10.3401C10.9891 13.0807 11.6895 15.4358 11.6895 15.5741C11.6895 15.7122 11.5473 15.9178 11.3735 16.0311C11.0684 16.2297 11.0219 16.2203 10.0354 15.7601C9.15316 15.3486 8.89103 15.2829 8.11921 15.2786C7.39919 15.2748 7.15661 15.3247 6.87343 15.5349C6.26599 15.9854 6.40234 16.1838 7.58013 16.5624C9.08156 17.045 9.90954 17.4807 11.0921 18.4106C12.7953 19.7499 13.4409 20.1555 14.2796 20.413C16.612 21.129 19.1302 20.0844 20.1034 17.9969C20.5948 16.9431 20.7141 15.8833 20.4738 14.7051C20.2497 13.6059 18.9631 9.55231 18.7589 9.30192C18.5868 9.09101 18.2289 9.10722 18.0392 9.33447C17.9118 9.48695 17.9448 9.73871 18.217 10.6923C18.5834 11.9752 18.5407 12.298 17.9862 12.4362C17.4636 12.5665 17.2128 12.3084 16.9282 11.3466C16.3638 9.44066 16.2573 9.21193 15.9161 9.17233C15.719 9.14943 15.5119 9.23124 15.3685 9.3888C15.1433 9.6361 15.1457 9.66841 15.4683 10.7371C15.8536 12.0132 15.8653 12.1578 15.6033 12.4184C15.3681 12.6521 14.7601 12.6743 14.5777 12.456C14.5057 12.3697 14.2671 11.6875 14.0473 10.9398C13.8277 10.1921 13.5977 9.48299 13.536 9.3638C13.3925 9.08581 13.029 9.0863 12.7754 9.36467C12.5936 9.56431 12.6073 9.68165 12.9429 10.8038C13.3952 12.3155 13.3997 12.5476 12.9828 12.8192C12.6936 13.0075 12.6214 13.0113 12.339 12.8531C12.1482 12.7461 11.9581 12.4766 11.8643 12.1796C11.7781 11.9073 11.2516 10.1538 10.6942 8.28314C10.1368 6.41232 9.61441 4.83967 9.53334 4.78843C9.29437 4.63742 8.98181 4.6774 8.77198 4.88583Z" fill="currentColor" />
    </svg>
  )
}

function PortIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 22" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M0 11C0 4.92487 4.92487 0 11 0C17.0751 0 22 4.92487 22 11C22 17.0751 17.0751 22 11 22C4.92487 22 0 17.0751 0 11ZM9.30354 5.02977C9.56324 5.28947 9.56324 5.71053 9.30354 5.97023L7.93877 7.335H15.3333C15.7006 7.335 15.9983 7.63273 15.9983 8C15.9983 8.36727 15.7006 8.665 15.3333 8.665H7.93877L9.30354 10.0298C9.56324 10.2895 9.56324 10.7105 9.30354 10.9702C9.04384 11.2299 8.62279 11.2299 8.36309 10.9702L5.86309 8.47023C5.73838 8.34551 5.66831 8.17637 5.66831 8C5.66831 7.82363 5.73838 7.65449 5.86309 7.52977L8.36309 5.02977C8.62279 4.77008 9.04384 4.77008 9.30354 5.02977ZM12.8598 11.9702C12.6001 11.7105 12.6001 11.2895 12.8598 11.0298C13.1195 10.7701 13.5405 10.7701 13.8002 11.0298L16.3002 13.5298C16.4249 13.6545 16.495 13.8236 16.495 14C16.495 14.1764 16.4249 14.3455 16.3002 14.4702L13.8002 16.9702C13.5405 17.2299 13.1195 17.2299 12.8598 16.9702C12.6001 16.7105 12.6001 16.2895 12.8598 16.0298L14.2246 14.665H6.83C6.46273 14.665 6.165 14.3673 6.165 14C6.165 13.6327 6.46273 13.335 6.83 13.335H14.2246L12.8598 11.9702Z" fill="currentColor" />
    </svg>
  )
}

function TonIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 23" fill="none">
      <path d="M18.4283 0H3.57085C0.839071 0 -0.892366 3.08068 0.481939 5.57102L9.65145 22.1863C10.2498 23.2712 11.7494 23.2712 12.3477 22.1863L21.5191 5.57102C22.8915 3.08459 21.1601 0 18.4302 0H18.4283ZM9.64397 17.2037L7.64702 13.1632L2.82854 4.15383C2.51067 3.57718 2.90333 2.83829 3.56898 2.83829H9.6421V17.2057L9.64397 17.2037ZM19.1669 4.15188L14.3503 13.1652L12.3533 17.2037V2.83633H18.4264C19.0921 2.83633 19.4847 3.57523 19.1669 4.15188Z" fill="currentColor" />
    </svg>
  )
}

function CodeIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 14" fill="none">
      <path d="M6.59065 3.33778C7.13645 2.80214 7.13645 1.93737 6.59065 1.40173C6.04486 0.86609 5.16369 0.86609 4.6179 1.40173L6.59065 3.33778ZM1.39573 6.5L0.409346 5.53197C-0.136449 6.06761 -0.136449 6.93238 0.409346 7.46803L1.39573 6.5ZM4.6179 11.5983C5.16369 12.1339 6.04486 12.1339 6.59065 11.5983C7.13645 11.0626 7.13645 10.1979 6.59065 9.66222L4.6179 11.5983ZM4.6179 1.40173L0.409346 5.53197L2.38211 7.46803L6.59065 3.33778L4.6179 1.40173ZM0.409346 7.46803L4.6179 11.5983L6.59065 9.66222L2.38211 5.53197L0.409346 7.46803Z" fill="currentColor"/>
      <path d="M16.3509 9.66222C15.883 10.1979 15.883 11.0626 16.3509 11.5983C16.8187 12.1339 17.574 12.1339 18.0418 11.5983L16.3509 9.66222ZM20.8037 6.5L21.6491 7.46803C22.117 6.93238 22.117 6.06761 21.6491 5.53197L20.8037 6.5ZM18.0418 1.40173C17.574 0.86609 16.8187 0.86609 16.3509 1.40173C15.883 1.93737 15.883 2.80214 16.3509 3.33778L18.0418 1.40173ZM18.0418 11.5983L21.6491 7.46803L19.9582 5.53197L16.3509 9.66222L18.0418 11.5983ZM21.6491 5.53197L18.0418 1.40173L16.3509 3.33778L19.9582 7.46803L21.6491 5.53197Z" fill="currentColor"/>
      <path d="M13.958 1.49763C14.1437 0.852295 13.6955 0.195636 12.972 0.0371299C12.2421 -0.127035 11.4994 0.269227 11.3201 0.908904L13.958 1.49763ZM8.042 12.5024C7.85632 13.1477 8.3045 13.8044 9.028 13.9629C9.75789 14.127 10.5006 13.7308 10.6799 13.0911L8.042 12.5024ZM11.3201 0.908904L8.042 12.5024L10.6799 13.0911L13.958 1.49763L11.3201 0.908904Z" fill="currentColor"/>
    </svg>
  )
}

function LanguageIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 22 21" fill="none">
      <path fillRule="evenodd" clipRule="evenodd" d="M12.5874 0.182551C12.3494 -0.0608503 11.9599 -0.0608503 11.7219 0.182551C11.4803 0.422318 11.4803 0.814667 11.7219 1.05443L14.0299 3.37946C14.268 3.62286 14.6575 3.62286 14.8955 3.37946C15.1371 3.13969 15.1371 2.74734 14.8955 2.50757L12.5874 0.182551ZM20.2329 5.88612H18.5307C18.4333 9.17385 17.5389 11.7641 15.761 13.5514C15.6781 13.6386 15.5915 13.7222 15.5013 13.8057C16.9872 14.6631 18.9346 15.1136 21.3869 15.1136C21.7259 15.1136 22 15.3897 22 15.7311C22 16.0726 21.7259 16.3487 21.3869 16.3487C18.574 16.3487 16.2479 15.7893 14.4627 14.6159C12.6776 15.7893 10.3515 16.3487 7.5385 16.3487C7.1995 16.3487 6.92542 16.0726 6.92542 15.7311C6.92542 15.3897 7.1995 15.1136 7.5385 15.1136C9.99083 15.1136 11.9383 14.6631 13.4241 13.8057C13.3339 13.7222 13.2474 13.6386 13.1644 13.5514C11.3865 11.7641 10.4921 9.17385 10.3947 5.88612H8.69254C8.35354 5.88612 8.07946 5.61003 8.07946 5.26854C8.07946 4.92705 8.35354 4.65096 8.69254 4.65096H20.2329C20.5719 4.65096 20.846 4.92705 20.846 5.26854C20.846 5.61003 20.5719 5.88612 20.2329 5.88612ZM11.6245 5.88612H17.3009C17.2035 8.95951 16.3705 11.1901 14.8955 12.6796C14.7584 12.8176 14.6142 12.9484 14.4627 13.0755C14.3112 12.9484 14.167 12.8176 14.0299 12.6796C12.5549 11.1901 11.7219 8.95951 11.6245 5.88612Z" fill="currentColor"/>
      <path fillRule="evenodd" clipRule="evenodd" d="M6.37726 7.36106C6.27988 7.12856 6.05629 6.97598 5.80745 6.97598C5.55861 6.97598 5.33502 7.12856 5.23765 7.36106L0.044491 20.1487C-0.0817308 20.4647 0.0697358 20.8244 0.383489 20.9552C0.697242 21.0823 1.05427 20.9298 1.1841 20.6137L2.91515 16.3487C2.91876 16.3487 2.91876 16.3487 2.92237 16.3487H8.69254C8.69614 16.3487 8.69614 16.3487 8.69975 16.3487L10.4308 20.6137C10.5606 20.9298 10.9177 21.0823 11.2314 20.9552C11.5452 20.8244 11.6966 20.4647 11.5704 20.1487L6.37726 7.36106ZM8.19847 15.1136L5.80745 9.22834L3.41644 15.1136H8.19847Z" fill="currentColor"/>
    </svg>
  )
}

function BookmarkIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none">
      <path d="M5 5C5 3.89543 5.89543 3 7 3H17C18.1046 3 19 3.89543 19 5V21L12 17.5L5 21V5Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

type EditingField = 'proxyPort' | 'homepage' | null

export function SettingsPage() {
  const { t } = useTranslation('settings')
  const { t: tc } = useTranslation('common')
  const [clearing, setClearing] = useState(false)
  const [cleared, setCleared] = useState(false)
  const [reconnecting, setReconnecting] = useState(false)
  const [showLogs, setShowLogs] = useState(false)
  const [logs, setLogs] = useState<string[]>([])
  const [loadingLogs, setLoadingLogs] = useState(false)
  const [editingField, setEditingField] = useState<EditingField>(null)
  const [editValue, setEditValue] = useState('')
  const [showLanguageSheet, setShowLanguageSheet] = useState(false)
  const [showBookmarksSheet, setShowBookmarksSheet] = useState(false)
  const [editingBookmarkId, setEditingBookmarkId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const [addingBookmark, setAddingBookmark] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newUrl, setNewUrl] = useState('')

  const { draft } = usePreferencesStore()
  const { status, disconnect, connect } = useProxyStore()
  const { bookmarks, resetBookmarks, addBookmark, removeBookmark, updateBookmark } = useBookmarksStore()
  const isConnected = status === 'connected'

  const fetchLogs = async () => {
    setLoadingLogs(true)
    try {
      const result = await TonProxy.getLogs()
      setLogs(result.logs?.split('\n').filter(Boolean) || [])
    } catch (e) {
      console.error('Failed to fetch logs:', e)
    } finally {
      setLoadingLogs(false)
    }
  }

  const openLogs = () => {
    setShowLogs(true)
    fetchLogs()
  }

  const workingDraft = draft ?? defaultPreferences
  const currentLanguage = SUPPORTED_LANGUAGES.find(l => l.code === workingDraft.language)

  const updateAndSave = <K extends keyof typeof workingDraft>(key: K, value: typeof workingDraft[K]) => {
    const store = usePreferencesStore.getState()
    const newPreferences = { ...store.preferences, [key]: value }

    if (key === 'theme') {
      document.documentElement.setAttribute('data-theme', value as string)
    }

    usePreferencesStore.setState({
      preferences: newPreferences,
      draft: newPreferences,
      hasChanges: false,
    })
  }

  const handleAnonymousModeChange = async (checked: boolean) => {
    updateAndSave('anonymousMode', checked)

    if (isConnected) {
      setReconnecting(true)
      try {
        await disconnect()
        await new Promise(resolve => setTimeout(resolve, 500))
        await connect()
      } catch (err) {
        console.error('Failed to reconnect:', err)
      } finally {
        setReconnecting(false)
      }
    }
  }

  const openEditor = (field: EditingField) => {
    if (field === 'proxyPort') {
      setEditValue(String(workingDraft.proxyPort))
    } else if (field === 'homepage') {
      setEditValue(workingDraft.homepage === 'ton://start' ? '' : workingDraft.homepage)
    }
    setEditingField(field)
  }

  const saveEditor = () => {
    if (editingField === 'proxyPort') {
      updateAndSave('proxyPort', parseInt(editValue) || 8080)
    } else if (editingField === 'homepage') {
      updateAndSave('homepage', editValue.trim() || 'ton://start')
    }
    setEditingField(null)
  }

  const handleClearData = async () => {
    setClearing(true)
    try {
      await platform.clearBrowsingData()
      resetBookmarks()
      setCleared(true)
      setTimeout(() => setCleared(false), 2000)
    } catch (err) {
      console.error('Failed to clear data:', err)
    } finally {
      setClearing(false)
    }
  }

  return (
    <div className="flex flex-col h-full bg-background-secondary overflow-auto scrollbar-hide">
      {/* Header */}
      <div className="bg-background border-b border-border px-5 py-4 pt-12 sticky top-0 z-10">
        <h1 className="text-lg font-semibold text-foreground">{t('title')}</h1>
      </div>

      <div className="px-3 pt-3 pb-24 space-y-4">
        {/* Network */}
        <SectionGroup label={t('network')}>
          <SettingsItem
            icon={AnonIcon}
            iconBg="bg-[#008BFF]"
            label={reconnecting ? t('reconnecting') : t('anonymous_mode')}
            description={reconnecting ? t('reconnecting_desc') : t('anonymous_mode_desc')}
            right={
              <Toggle
                checked={workingDraft.anonymousMode}
                onChange={handleAnonymousModeChange}
                disabled={reconnecting}
              />
            }
            disabled={reconnecting}
          />
          <SettingsItem
            icon={WifiIcon}
            iconBg="bg-blue-500"
            label={t('auto_connect')}
            description={t('auto_connect_desc')}
            right={
              <Toggle
                checked={workingDraft.autoConnect}
                onChange={(checked) => updateAndSave('autoConnect', checked)}
              />
            }
          />
          <SettingsItem
            icon={PortIcon}
            iconBg="bg-slate-500"
            label={t('proxy_port')}
            description={t('proxy_port_desc')}
            onClick={() => openEditor('proxyPort')}
            right={
              <div className="flex items-center gap-1">
                <span className="text-[13px] text-muted-foreground">{workingDraft.proxyPort}</span>
                <ChevronRight className="h-5 w-5 text-muted-foreground/60" />
              </div>
            }
          />
        </SectionGroup>

        {/* General */}
        <SectionGroup label={t('general')}>
          <SettingsItem
            icon={HomeIcon}
            iconBg="bg-orange-500"
            label={t('homepage')}
            description={t('homepage_desc')}
            onClick={() => openEditor('homepage')}
            right={
              <div className="flex items-center gap-1">
                <span className="text-[13px] text-muted-foreground truncate max-w-[120px]">
                  {workingDraft.homepage === 'ton://start' ? t('start_page') : workingDraft.homepage}
                </span>
                <ChevronRight className="h-5 w-5 text-muted-foreground/60" />
              </div>
            }
          />
          <SettingsItem
            icon={LanguageIcon}
            iconBg="bg-violet-500"
            label={t('language')}
            description={t('language_desc')}
            onClick={() => setShowLanguageSheet(true)}
            right={
              <div className="flex items-center gap-1">
                <span className="text-[13px] text-muted-foreground">
                  {currentLanguage?.nativeLabel}
                </span>
                <ChevronRight className="h-5 w-5 text-muted-foreground/60" />
              </div>
            }
          />
          <SettingsItem
            icon={BookmarkIcon}
            iconBg="bg-purple-500"
            label={t('bookmarks')}
            description={t('bookmarks_desc', { count: bookmarks.length })}
            onClick={() => setShowBookmarksSheet(true)}
            right={
              <div className="flex items-center gap-1">
                <span className="text-[13px] text-muted-foreground">{bookmarks.length}</span>
                <ChevronRight className="h-5 w-5 text-muted-foreground/60" />
              </div>
            }
          />
        </SectionGroup>

        {/* Privacy */}
        <SectionGroup label={t('privacy')}>
          <SettingsItem
            icon={JsIcon}
            iconBg="bg-amber-500"
            label={t('javascript')}
            description={t('javascript_desc')}
            right={
              <Toggle
                checked={workingDraft.javaScriptEnabled}
                onChange={(checked) => updateAndSave('javaScriptEnabled', checked)}
              />
            }
          />
          <SettingsItem
            icon={Cookie}
            iconBg="bg-yellow-700"
            label={t('third_party_cookies')}
            description={t('third_party_cookies_desc')}
            right={
              <Toggle
                checked={workingDraft.thirdPartyCookies}
                onChange={(checked) => {
                  updateAndSave('thirdPartyCookies', checked)
                  TonProxy.setThirdPartyCookies({ enabled: checked }).catch(() => {})
                }}
              />
            }
          />
          <SettingsItem
            icon={FlameIcon}
            iconBg="bg-cyan-500"
            label={t('clear_on_exit')}
            description={t('clear_on_exit_desc')}
            right={
              <Toggle
                checked={workingDraft.clearOnExit}
                onChange={(checked) => updateAndSave('clearOnExit', checked)}
              />
            }
          />
          <SettingsItem
            icon={TrashIcon}
            iconBg="bg-red-500"
            label={cleared ? t('cleared') : clearing ? t('clearing') : t('clear_browsing_data')}
            description={t('clear_data_desc')}
            onClick={handleClearData}
            disabled={clearing}
            right={<ChevronRight className="h-5 w-5 text-muted-foreground/60" />}
          />
        </SectionGroup>

        {/* Debug */}
        <SectionGroup label={t('debug')}>
          <SettingsItem
            icon={CodeIcon}
            iconBg="bg-emerald-500"
            label={t('proxy_logs')}
            description={t('proxy_logs_desc')}
            onClick={openLogs}
            right={<ChevronRight className="h-5 w-5 text-muted-foreground/60" />}
          />
        </SectionGroup>

        {/* About */}
        <SectionGroup label={t('about')}>
          <SettingsItem
            icon={TonIcon}
            iconBg="bg-primary"
            label={APP_NAME}
            description={t('about_desc')}
            right={
              <span className="text-[13px] text-muted-foreground">v{APP_VERSION}</span>
            }
          />
        </SectionGroup>
      </div>

      {/* Edit Field Sheet */}
      <BottomSheet
        open={editingField !== null}
        onClose={() => setEditingField(null)}
        title={editingField === 'proxyPort' ? t('proxy_port') : t('homepage')}
        showHandle
        showCloseButton={false}
        maxHeight="60vh"
      >
        {editingField !== null && (
          <div className="space-y-4">
            <p className="text-[13px] text-muted-foreground">
              {editingField === 'proxyPort'
                ? t('proxy_port_edit_desc')
                : t('homepage_edit_desc')}
            </p>
            <EditInput
              type={editingField === 'proxyPort' ? 'number' : 'text'}
              value={editValue}
              onChange={setEditValue}
              placeholder={editingField === 'proxyPort' ? '8080' : 'ton://start'}
              autoFocus
            />
            <div className="flex gap-3">
              <Button
                variant="ghost"
                className="flex-1"
                onClick={() => setEditingField(null)}
              >
                {tc('cancel')}
              </Button>
              <Button
                className="flex-1"
                onClick={saveEditor}
              >
                {tc('save')}
              </Button>
            </div>
          </div>
        )}
      </BottomSheet>

      {/* Language Selection Sheet */}
      <BottomSheet
        open={showLanguageSheet}
        onClose={() => setShowLanguageSheet(false)}
        title={t('language')}
        showHandle
        showCloseButton={false}
        maxHeight="60vh"
      >
        <div className="space-y-1">
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => {
                updateAndSave('language', lang.code)
                setShowLanguageSheet(false)
              }}
              className={cn(
                "w-full flex items-center justify-between px-4 py-3 rounded-xl transition-colors",
                workingDraft.language === lang.code
                  ? "bg-primary/10 text-primary"
                  : "active:bg-muted/50 text-foreground"
              )}
            >
              <div className="flex flex-col items-start">
                <span className="text-base font-medium">{lang.nativeLabel}</span>
                <span className="text-[13px] text-muted-foreground">{lang.label}</span>
              </div>
              {workingDraft.language === lang.code && (
                <div className="w-5 h-5 rounded-full bg-primary flex items-center justify-center">
                  <svg className="w-3 h-3 text-primary-foreground" viewBox="0 0 12 12" fill="none">
                    <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
              )}
            </button>
          ))}
        </div>
      </BottomSheet>

      {/* Bookmarks Management Sheet */}
      <BottomSheet
        open={showBookmarksSheet}
        onClose={() => {
          setShowBookmarksSheet(false)
          setEditingBookmarkId(null)
          setAddingBookmark(false)
        }}
        title={t('bookmarks')}
        showHandle
        showCloseButton={false}
        maxHeight="70vh"
      >
        <div className="space-y-1">
          {bookmarks.length === 0 && !addingBookmark && (
            <p className="text-muted-foreground text-center py-8 text-[13px]">{t('bookmarks_empty')}</p>
          )}
          {bookmarks.map((bm) => (
            <div key={bm.id} className="px-2">
              {editingBookmarkId === bm.id ? (
                <div className="bg-background-secondary rounded-lg p-3 space-y-2">
                  <input
                    className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    placeholder={tc('title')}
                  />
                  <input
                    className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                    value={editUrl}
                    onChange={(e) => setEditUrl(e.target.value)}
                    placeholder="URL"
                  />
                  <div className="flex gap-2 justify-end">
                    <button
                      onClick={() => setEditingBookmarkId(null)}
                      className="p-2 rounded-lg text-muted-foreground active:bg-muted/50"
                    >
                      <X className="h-4 w-4" />
                    </button>
                    <button
                      onClick={() => {
                        if (editTitle.trim() && editUrl.trim()) {
                          updateBookmark(bm.id, { title: editTitle.trim(), url: editUrl.trim() })
                          setEditingBookmarkId(null)
                        }
                      }}
                      className="p-2 rounded-lg text-primary active:bg-primary/10"
                    >
                      <Check className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ) : (
                <div className="flex items-center gap-3 px-2 py-2.5 rounded-xl active:bg-muted/50 transition-colors">
                  {bm.favicon ? (
                    <img src={bm.favicon} className="w-5 h-5 rounded shrink-0" alt="" />
                  ) : (
                    <Globe className="w-5 h-5 text-muted-foreground shrink-0" />
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">{bm.title}</p>
                    <p className="text-[12px] text-muted-foreground truncate">{bm.url}</p>
                  </div>
                  <button
                    onClick={() => {
                      setEditingBookmarkId(bm.id)
                      setEditTitle(bm.title)
                      setEditUrl(bm.url)
                    }}
                    className="p-2 rounded-lg text-muted-foreground active:bg-muted/50 shrink-0"
                  >
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => removeBookmark(bm.id)}
                    className="p-2 rounded-lg text-red-400 active:bg-red-500/10 shrink-0"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              )}
            </div>
          ))}

          {addingBookmark ? (
            <div className="px-2">
              <div className="bg-background-secondary rounded-lg p-3 space-y-2">
                <input
                  className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder={tc('title')}
                  autoFocus
                />
                <input
                  className="w-full bg-background rounded-lg px-3 py-2 text-sm text-foreground outline-none border border-border"
                  value={newUrl}
                  onChange={(e) => setNewUrl(e.target.value)}
                  placeholder="URL"
                />
                <div className="flex gap-2 justify-end">
                  <button
                    onClick={() => { setAddingBookmark(false); setNewTitle(''); setNewUrl('') }}
                    className="p-2 rounded-lg text-muted-foreground active:bg-muted/50"
                  >
                    <X className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => {
                      if (newTitle.trim() && newUrl.trim()) {
                        addBookmark(newUrl.trim(), newTitle.trim())
                        setAddingBookmark(false)
                        setNewTitle('')
                        setNewUrl('')
                      }
                    }}
                    className="p-2 rounded-lg text-primary active:bg-primary/10"
                  >
                    <Check className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setAddingBookmark(true)}
              className="w-full flex items-center gap-2 px-4 py-3 rounded-xl text-primary active:bg-primary/10 transition-colors"
            >
              <Plus className="h-4 w-4" />
              <span className="text-sm font-medium">{t('bookmarks_add')}</span>
            </button>
          )}
        </div>
      </BottomSheet>

      {/* Log Modal */}
      {showLogs && (
        <div className="fixed inset-0 z-[60] bg-black/90 flex flex-col" role="dialog" aria-modal="true" aria-label={t('proxy_logs')}>
          <div className="flex items-center justify-between px-5 py-3 pt-12 border-b border-border bg-background">
            <span className="text-lg font-medium text-foreground">{t('proxy_logs')}</span>
            <div className="flex gap-1">
              <button onClick={fetchLogs} disabled={loadingLogs} className="p-3 rounded-xl text-muted-foreground active:bg-muted/50 transition-colors duration-200">
                <RefreshCw className={cn("h-5 w-5", loadingLogs && "animate-spin")} />
              </button>
              <button onClick={() => setShowLogs(false)} className="p-3 rounded-xl text-muted-foreground active:bg-muted/50 transition-colors duration-200">
                <X className="h-5 w-5" />
              </button>
            </div>
          </div>
          <div className="flex-1 overflow-auto p-3 font-mono text-xs">
            {logs.length === 0 ? (
              <p className="text-muted-foreground text-center py-8">
                {loadingLogs ? t('logs_loading') : t('logs_empty')}
              </p>
            ) : (
              logs.map((line, i) => (
                <div key={i} className={cn(
                  "py-0.5 text-muted-foreground",
                  line.includes('[PROXY]') && "text-green-400",
                  line.includes('[tonnet-proxy]') && "text-purple-400",
                  line.includes('Error') && "text-red-400",
                )}>
                  {line}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// Section group: glassmorphism card (matches TelegramTabBar nav style)
function SectionGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-[15px] font-semibold text-primary px-2 mb-1">
        {label}
      </p>
      <div className="relative rounded-2xl overflow-hidden shadow-[0px_8px_40px_0px_rgba(0,0,0,0.12)]">
        <div className="absolute inset-0 rounded-2xl bg-[rgba(255,255,255,0.08)]" />
        <div className="absolute inset-0 rounded-2xl bg-background/70 backdrop-blur-2xl" />
        <div className="relative">{children}</div>
      </div>
    </div>
  )
}

// Settings row (Telegram: 50dp height, 21dp hpad, 28px icon, 16sp label, 13sp desc)
function SettingsItem({
  icon: Icon,
  iconBg,
  label,
  description,
  right,
  onClick,
  disabled = false,
}: {
  icon: React.ElementType
  iconBg: string
  label: string
  description?: string
  right?: React.ReactNode
  onClick?: () => void
  disabled?: boolean
}) {
  const Wrapper = onClick ? 'button' : 'div'

  return (
    <Wrapper
      onClick={disabled ? undefined : onClick}
      className={cn(
        "flex items-center gap-2 px-5 w-full text-left",
        onClick && !disabled && "active:bg-muted/50 transition-colors duration-200",
        disabled && "opacity-50",
      )}
    >
      {/* Icon badge: 28x28dp, radius 9dp (Telegram exact) */}
      <div className={cn("w-7 h-7 rounded-[9px] flex items-center justify-center shrink-0", iconBg)}>
        <Icon className="h-5 w-5 text-primary-foreground" />
      </div>
      {/* Content + divider — padding here so border sits perfectly centered */}
      <div className={cn(
        "flex-1 min-w-0 flex items-center justify-between py-2.5",
      )}>
        <div className="flex-1 min-w-0">
          <p className="text-base font-medium text-foreground leading-tight">{label}</p>
          {description && (
            <p className="text-[13px] text-muted-foreground leading-tight mt-0.5">{description}</p>
          )}
        </div>
        {right && <div className="shrink-0 ml-3">{right}</div>}
      </div>
    </Wrapper>
  )
}

// Auto-focus input for edit sheets
function EditInput({
  type,
  value,
  onChange,
  placeholder,
  autoFocus,
}: {
  type: string
  value: string
  onChange: (v: string) => void
  placeholder: string
  autoFocus?: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (autoFocus) {
      const timer = setTimeout(() => inputRef.current?.focus(), 350)
      return () => clearTimeout(timer)
    }
  }, [autoFocus])

  return (
    <input
      ref={inputRef}
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full px-4 py-4 bg-background-secondary border border-border rounded-xl text-foreground text-base placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring transition-shadow duration-200"
    />
  )
}

// Toggle switch (MD3: 52x32dp track, 24-28dp thumb)
function Toggle({
  checked,
  onChange,
  disabled = false,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
}) {
  return (
    <button
      onClick={(e) => {
        e.stopPropagation()
        if (!disabled) onChange(!checked)
      }}
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      className={cn(
        "w-[52px] h-8 rounded-full p-1 transition-colors duration-200 shrink-0",
        checked ? 'bg-primary' : 'bg-border',
        disabled && 'opacity-50 cursor-not-allowed',
      )}
    >
      <div
        className={cn(
          'w-6 h-6 rounded-full bg-primary-foreground shadow transition-transform duration-200',
          checked ? 'translate-x-5' : 'translate-x-0',
        )}
      />
    </button>
  )
}
