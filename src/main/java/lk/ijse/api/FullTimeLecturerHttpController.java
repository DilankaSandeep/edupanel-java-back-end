package lk.ijse.api;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lecturers/full-time")
@CrossOrigin
public class FullTimeLecturerHttpController {
 @PatchMapping("/ranks")
    public void arrangeFullTimeLecturerOrder(){
     System.out.println("arrangeFullTimeLecturerOrder()");
 }

 @GetMapping
    public void getAllFullTimeLectuers(){
     System.out.println("getAllFullTimeLecturers()");
 }



}
